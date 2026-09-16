package cn.iantech.id.core;

import cn.iantech.id.IdGenerationException;
import cn.iantech.id.autoconfigure.IdGeneratorProperties;
import cn.iantech.redis.IRedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.stream.IntStream;

/**
 * 通过 Redis 原子脚本持有应用实例级的 Worker ID 租约。
 *
 * <p>Worker ID 是**实例级**资源：一个应用实例只持有一个，实例内所有业务共用它。
 * 池就是整个 {@code [0, 2^workerIdBitLength)} 区间，不再按业务静态分块，
 * 因此业务数量不受池容量限制，池容量只约束实例（副本）数量。
 *
 * <p>位宽一致性由两级守卫保证，两级都使用独立于历史实现的键名：
 * <ul>
 *   <li>服务级 {@code {ns}:worker:pool} 存 {@code workerIdBitLength} —— Worker ID 的数值空间。</li>
 *   <li>业务级 {@code {ns}:worker:layout:<业务>} 存 {@code workerIdBitLength:sequenceBitLength}
 *       —— 该业务表的 ID 位布局。</li>
 * </ul>
 * 分层后，新增业务只会写入新的业务级键，老实例从不读取它，因此新增业务可以滚动发布；
 * 只有改动已声明业务的序列位宽才需要停机（fail-closed）。
 */
final class RedisWorkerLease implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RedisWorkerLease.class);

    /** 脚本返回码：当前命名空间已使用不同的 Worker ID 位宽。 */
    private static final long ACQUIRE_POOL_MISMATCH = -2L;

    /** 脚本返回码：某个已声明业务的序列位宽与历史不一致。 */
    private static final long ACQUIRE_BUSINESS_LAYOUT_MISMATCH = -3L;

    /** 脚本返回码：池内已无空闲 Worker ID。 */
    private static final long ACQUIRE_EXHAUSTED = -1L;

    /** 续租成功。 */
    private static final long RENEW_SUCCESS = 1L;

    /**
     * 键位约定：KEYS[1]=游标，KEYS[2]=服务级位宽守卫，
     * KEYS[3 .. 2+业务数]=业务级位宽守卫，其后为整池的租约键（下标即 Worker ID）。
     * ARGV[1]=池容量，ARGV[2]=租约毫秒，ARGV[3]=持有者，ARGV[4]=服务级位宽，
     * ARGV[5]=业务数，ARGV[6 ..]=各业务位宽。
     */
    private static final String ACQUIRE_SCRIPT = """
            local cursor = redis.call('INCR', KEYS[1])
            local poolCount = tonumber(ARGV[1])
            local leaseMillis = tonumber(ARGV[2])
            local owner = ARGV[3]
            local poolLayout = ARGV[4]
            local businessCount = tonumber(ARGV[5])
            local currentPool = redis.call('GET', KEYS[2])
            if currentPool and currentPool ~= poolLayout then
                return -2
            end
            if not currentPool then
                redis.call('SET', KEYS[2], poolLayout)
            end
            for index = 1, businessCount do
                local current = redis.call('GET', KEYS[2 + index])
                local expected = ARGV[5 + index]
                if current and current ~= expected then
                    return -3
                end
                if not current then
                    redis.call('SET', KEYS[2 + index], expected)
                end
            end
            local leaseKeyBase = 2 + businessCount
            for offset = 0, poolCount - 1 do
                local slot = (cursor - 1 + offset) % poolCount
                local acquired = redis.call('SET', KEYS[leaseKeyBase + 1 + slot], owner, 'NX', 'PX', leaseMillis)
                if acquired then
                    redis.call('SET', KEYS[1], cursor + offset)
                    return slot
                end
            end
            return -1
            """;

    private static final String RENEW_SCRIPT = """
            local owner = ARGV[1]
            local leaseMillis = tonumber(ARGV[2])
            if redis.call('GET', KEYS[1]) == owner then
                redis.call('PEXPIRE', KEYS[1], leaseMillis)
                return 1
            end
            return 0
            """;

    /**
     * 重新获取自己那个 Worker ID 的租约。
     *
     * <p>只对当前实例已经持有的 {@code leaseKey} 做原子 {@code SET NX}：
     * 键不存在（如 Redis 重启丢失数据）时抢回同一个 Worker ID 并恢复出号；
     * 键已被其他实例持有时 {@code SET NX} 失败，本实例继续保持停发。
     *
     * <p>之所以不去重新扫描整个池，是因为算法侧的 {@code SnowflakeIdGenerator} 在构造时就绑定了
     * Worker ID，换一个 ID 需要重建生成器；而拿回原 ID 在语义上也是安全的——其他实例只可能在
     * 本实例的租约键过期后才能抢到它，而本实例的本地安全窗口严格短于租约 TTL，
     * 因此本实例早已停发，不存在两个实例同时以同一 Worker ID 出号的窗口。
     */
    private static final String REACQUIRE_SCRIPT = """
            local owner = ARGV[1]
            local leaseMillis = tonumber(ARGV[2])
            if redis.call('SET', KEYS[1], owner, 'NX', 'PX', leaseMillis) then
                return 1
            end
            return 0
            """;

    private static final String RELEASE_SCRIPT = """
            local owner = ARGV[1]
            if redis.call('GET', KEYS[1]) == owner then
                redis.call('DEL', KEYS[1])
                return 1
            end
            return 0
            """;

    private final IRedisService redisService;
    private final String leaseKey;
    private final String ownerToken;
    private final long localSafetyNanos;
    private final long leaseMillis;
    private final LongSupplier nanoTime;
    private final AtomicLong validUntilNanos = new AtomicLong();
    /** 是否持有有效租约。续租失败时置 false（停发），续租或重取成功后恢复 true。 */
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final int workerId;

    RedisWorkerLease(IRedisService redisService, IdGeneratorProperties properties) {
        this(redisService, properties, System::nanoTime, UUID.randomUUID().toString());
    }

    RedisWorkerLease(IRedisService redisService, IdGeneratorProperties properties,
                     LongSupplier nanoTime, String ownerToken) {
        this.redisService = Objects.requireNonNull(redisService, "Redis 服务不能为空");
        Objects.requireNonNull(properties, "ID 生成器配置不能为空").validate();
        this.nanoTime = Objects.requireNonNull(nanoTime, "单调时钟不能为空");
        this.ownerToken = Objects.requireNonNull(ownerToken, "租约所有者不能为空");
        Duration leaseDuration = properties.getLeaseDuration();
        this.localSafetyNanos = leaseDuration.minus(properties.getRenewInterval()).toNanos();
        this.leaseMillis = leaseDuration.toMillis();

        String prefix = workerPrefix(properties);
        Map<String, String> businessLayouts = businessLayouts(properties);
        int poolSize = properties.workerPoolSize();

        List<String> keys = new ArrayList<>(2 + businessLayouts.size() + poolSize);
        keys.add(prefix + ":cursor");
        keys.add(prefix + ":pool");
        businessLayouts.keySet().forEach(business -> keys.add(prefix + ":layout:" + business));
        List<String> leaseKeys = IntStream.range(0, poolSize)
                .mapToObj(slot -> prefix + ":lease:" + slot)
                .toList();
        keys.addAll(leaseKeys);

        List<Object> arguments = new ArrayList<>(5 + businessLayouts.size());
        arguments.add(poolSize);
        arguments.add(leaseMillis);
        arguments.add(ownerToken);
        arguments.add(poolLayout(properties));
        arguments.add(businessLayouts.size());
        arguments.addAll(businessLayouts.values());

        Long acquiredWorkerId;
        long requestStartedNanos = nanoTime.getAsLong();
        try {
            acquiredWorkerId = redisService.executeLongScript(ACQUIRE_SCRIPT, keys, arguments);
        } catch (RuntimeException exception) {
            throw new IdGenerationException("无法从 Redis 获取 Worker ID 租约", exception);
        }
        if (Long.valueOf(ACQUIRE_POOL_MISMATCH).equals(acquiredWorkerId)) {
            throw new IdGenerationException("当前命名空间已使用不同的 worker-id-bit-length："
                    + poolLayout(properties));
        }
        if (Long.valueOf(ACQUIRE_BUSINESS_LAYOUT_MISMATCH).equals(acquiredWorkerId)) {
            throw new IdGenerationException("已声明业务的 sequence-bit-length 与历史不一致，需先停机再变更："
                    + businessLayouts);
        }
        if (acquiredWorkerId == null || acquiredWorkerId == ACQUIRE_EXHAUSTED) {
            throw new IdGenerationException("没有可用的 Worker ID 租约，池已耗尽：容量 "
                    + poolSize + "，请提升 worker-id-bit-length 或减少副本数");
        }
        if (acquiredWorkerId < 0 || acquiredWorkerId >= poolSize) {
            throw new IdGenerationException("Redis 返回了池外的 Worker ID：" + acquiredWorkerId
                    + "，池容量 " + poolSize);
        }
        this.workerId = Math.toIntExact(acquiredWorkerId);
        this.leaseKey = leaseKeys.get(this.workerId);
        extendLocalSafetyPeriod(requestStartedNanos);
    }

    private static String workerPrefix(IdGeneratorProperties properties) {
        return "{" + properties.getNamespace() + "}:worker";
    }

    private static String poolLayout(IdGeneratorProperties properties) {
        return String.valueOf(properties.getWorkerIdBitLength());
    }

    /**
     * 各已声明业务的 ID 位布局，键为业务名、值为 {@code workerIdBitLength:sequenceBitLength}。
     *
     * <p>按业务名排序，保证同一配置在不同实例上生成完全一致的键与参数序列。
     */
    private static Map<String, String> businessLayouts(IdGeneratorProperties properties) {
        Map<String, String> layouts = new TreeMap<>();
        properties.getBusinesses().keySet().forEach(business -> layouts.put(business,
                properties.getWorkerIdBitLength() + ":" + properties.sequenceBitLengthFor(business)));
        return layouts;
    }

    int workerId() {
        return workerId;
    }

    boolean isValid() {
        return active.get() && !closed.get() && nanoTime.getAsLong() - validUntilNanos.get() < 0;
    }

    /**
     * 续租一次。失败时立即停发，但**不**永久放弃：下一个续租周期会再次尝试，
     * 因此 Redis 重启、网络抖动等临时故障恢复后能自动回到可用状态。
     *
     * <p>租约是实例级的，因此一次续租失败会同时停掉本实例内的全部业务生成器。
     */
    void renew() {
        if (closed.get()) {
            return;
        }
        long requestStartedNanos = nanoTime.getAsLong();
        try {
            if (Long.valueOf(RENEW_SUCCESS).equals(
                    redisService.executeLongScript(RENEW_SCRIPT, List.of(leaseKey),
                            List.of(ownerToken, leaseMillis)))) {
                resume(requestStartedNanos);
                return;
            }
            // 续租被拒：租约键已不存在（Redis 丢数据）或已归属其他实例。
            if (Long.valueOf(RENEW_SUCCESS).equals(
                    redisService.executeLongScript(REACQUIRE_SCRIPT, List.of(leaseKey),
                            List.of(ownerToken, leaseMillis)))) {
                log.warn("Worker ID 租约键丢失后已重新获取，恢复出号: leaseKey={}, workerId={}",
                        leaseKey, workerId);
                resume(requestStartedNanos);
                return;
            }
            log.warn("Worker ID 租约已归属其他实例，本实例全部业务保持停发: leaseKey={}, workerId={}",
                    leaseKey, workerId);
        } catch (RuntimeException exception) {
            // 严格停发：无法确认租约仍归属当前实例时立即停发，等待下一个续租周期重试。
            log.warn("Worker ID 租约续租失败，本实例全部业务暂停出号: leaseKey={}", leaseKey, exception);
        }
        suspend();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        suspend();
        try {
            redisService.executeLongScript(
                    RELEASE_SCRIPT,
                    List.of(leaseKey),
                    List.of(ownerToken));
        } catch (RuntimeException exception) {
            // 关闭时释放失败由 Redis 租约超时兜底，不能恢复本地生成能力。
            log.warn("Worker ID 租约释放失败，等待租约超时兜底: leaseKey={}", leaseKey, exception);
        }
    }

    private void extendLocalSafetyPeriod(long requestStartedNanos) {
        // 预留一个续租周期作为边界安全窗口，并从请求发出前起算以扣除网络往返时间。
        validUntilNanos.set(saturatedAdd(requestStartedNanos, localSafetyNanos));
    }

    /** 确认持有租约，恢复出号能力。 */
    private void resume(long requestStartedNanos) {
        active.set(true);
        extendLocalSafetyPeriod(requestStartedNanos);
    }

    /** 停发但保留重试机会：下一个续租周期仍会尝试续租或重取租约。 */
    private void suspend() {
        active.set(false);
        validUntilNanos.set(0L);
    }

    private long saturatedAdd(long value, long increment) {
        try {
            return Math.addExact(value, increment);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
