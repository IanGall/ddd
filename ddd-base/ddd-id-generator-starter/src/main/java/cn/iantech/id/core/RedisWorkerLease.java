package cn.iantech.id.core;

import cn.iantech.id.IdGenerationException;
import cn.iantech.id.autoconfigure.IdGeneratorProperties;
import cn.iantech.redis.IRedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * 通过 Redis 原子脚本持有一个 Worker ID 租约。
 *
 * <p>租约只在给定的 Worker ID 区间内轮询：默认区间是整个池，业务模式下是
 * {@code [businessIndex * blockSize, (businessIndex + 1) * blockSize)}。
 * lease 键与位宽键始终按 namespace 共享，因此区间只是容量与扫描范围的约束，
 * Worker ID 的排他性仍由 {@code SET NX} 仲裁。
 */
final class RedisWorkerLease implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RedisWorkerLease.class);

    /** 脚本返回码：当前命名空间已使用不同的位宽布局。 */
    private static final long ACQUIRE_LAYOUT_MISMATCH = -2L;

    /** 脚本返回码：续租成功。 */
    private static final long RENEW_SUCCESS = 1L;

    private static final String ACQUIRE_SCRIPT = """
            local cursor = redis.call('INCR', KEYS[1])
            local rangeStart = tonumber(ARGV[1])
            local rangeCount = tonumber(ARGV[2])
            local leaseMillis = tonumber(ARGV[3])
            local owner = ARGV[4]
            local layout = ARGV[5]
            local currentLayout = redis.call('GET', KEYS[2])
            if currentLayout and currentLayout ~= layout then
                return -2
            end
            if not currentLayout then
                redis.call('SET', KEYS[2], layout)
            end
            for offset = 0, rangeCount - 1 do
                local slot = (cursor - 1 + offset) % rangeCount
                local workerId = rangeStart + slot
                local acquired = redis.call('SET', KEYS[3 + slot], owner, 'NX', 'PX', leaseMillis)
                if acquired then
                    redis.call('SET', KEYS[1], cursor + offset)
                    return workerId
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
     * <p>之所以不去重新扫描整个区间，是因为算法侧的 {@code SnowflakeIdGenerator} 在构造时就绑定了
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
        this(redisService, properties, defaultCursorKey(properties), 0,
                properties.workerPoolSize(), System::nanoTime, UUID.randomUUID().toString());
    }

    RedisWorkerLease(IRedisService redisService, IdGeneratorProperties properties,
                     LongSupplier nanoTime, String ownerToken) {
        this(redisService, properties, defaultCursorKey(properties), 0,
                properties.workerPoolSize(), nanoTime, ownerToken);
    }

    /**
     * 为指定业务块租用一个 Worker ID。
     *
     * <p>块 {@code i} 可用区间为 {@code [i * blockSize, (i + 1) * blockSize)}，
     * 各业务块的 lease 键与位宽键共享，因此 Worker ID 排他性与位宽一致性仍由同一 namespace 仲裁。
     */
    static RedisWorkerLease forBlock(IRedisService redisService, IdGeneratorProperties properties,
                                     int businessIndex) {
        return new RedisWorkerLease(redisService, properties, blockCursorKey(properties, businessIndex),
                properties.blockStart(businessIndex), properties.getWorkerIdBlockSize(),
                System::nanoTime, UUID.randomUUID().toString());
    }

    RedisWorkerLease(IRedisService redisService, IdGeneratorProperties properties, String cursorKey,
                     int rangeStart, int rangeCount, LongSupplier nanoTime, String ownerToken) {
        this.redisService = Objects.requireNonNull(redisService, "Redis 服务不能为空");
        Objects.requireNonNull(properties, "ID 生成器配置不能为空").validate();
        this.nanoTime = Objects.requireNonNull(nanoTime, "单调时钟不能为空");
        this.ownerToken = Objects.requireNonNull(ownerToken, "租约所有者不能为空");
        Duration leaseDuration = properties.getLeaseDuration();
        this.localSafetyNanos = leaseDuration.minus(properties.getRenewInterval()).toNanos();
        this.leaseMillis = leaseDuration.toMillis();

        String prefix = workerPrefix(properties);
        String layoutKey = prefix + ":layout";
        List<String> leaseKeys = IntStream.range(0, rangeCount)
                .mapToObj(slot -> prefix + ":lease:" + (rangeStart + slot))
                .toList();

        Long acquiredWorkerId;
        long requestStartedNanos = nanoTime.getAsLong();
        try {
            acquiredWorkerId = redisService.executeLongScript(
                    ACQUIRE_SCRIPT,
                    Stream.concat(Stream.of(cursorKey, layoutKey), leaseKeys.stream()).toList(),
                    List.of(rangeStart, rangeCount, leaseMillis, ownerToken, layout(properties)));
        } catch (RuntimeException exception) {
            throw new IdGenerationException("无法从 Redis 获取 Worker ID 租约", exception);
        }
        if (Long.valueOf(ACQUIRE_LAYOUT_MISMATCH).equals(acquiredWorkerId)) {
            throw new IdGenerationException("当前命名空间已使用不同的 Worker ID 与序列位宽");
        }
        if (acquiredWorkerId == null || acquiredWorkerId == -1L) {
            throw new IdGenerationException("没有可用的 Worker ID 租约，区间 ["
                    + rangeStart + ", " + (rangeStart + rangeCount) + ")");
        }
        if (acquiredWorkerId < rangeStart || acquiredWorkerId >= rangeStart + rangeCount) {
            throw new IdGenerationException("Redis 返回了区间外的 Worker ID：" + acquiredWorkerId
                    + "，区间 [" + rangeStart + ", " + (rangeStart + rangeCount) + ")");
        }
        this.workerId = Math.toIntExact(acquiredWorkerId);
        this.leaseKey = leaseKeys.get(workerId - rangeStart);
        extendLocalSafetyPeriod(requestStartedNanos);
    }

    private static String defaultCursorKey(IdGeneratorProperties properties) {
        return workerPrefix(properties) + ":cursor";
    }

    private static String blockCursorKey(IdGeneratorProperties properties, int businessIndex) {
        return workerPrefix(properties) + ":cursor:" + businessIndex;
    }

    private static String workerPrefix(IdGeneratorProperties properties) {
        return "{" + properties.getNamespace() + "}:worker";
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
            log.warn("Worker ID 租约已归属其他实例，保持停发: leaseKey={}, workerId={}",
                    leaseKey, workerId);
        } catch (RuntimeException exception) {
            // 严格停发：无法确认租约仍归属当前实例时立即停发，等待下一个续租周期重试。
            log.warn("Worker ID 租约续租失败，暂停出号: leaseKey={}", leaseKey, exception);
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

    private static String layout(IdGeneratorProperties properties) {
        return properties.getWorkerIdBitLength() + ":" + properties.getSequenceBitLength();
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
