package cn.iantech.id;

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
 */
final class RedisWorkerLease implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RedisWorkerLease.class);

    /** 脚本返回码：当前命名空间已使用不同的位宽布局。 */
    private static final long ACQUIRE_LAYOUT_MISMATCH = -2L;

    /** 脚本返回码：续租成功。 */
    private static final long RENEW_SUCCESS = 1L;

    private static final String ACQUIRE_SCRIPT = """
            local cursor = redis.call('INCR', KEYS[1])
            local poolSize = tonumber(ARGV[1])
            local leaseMillis = tonumber(ARGV[2])
            local owner = ARGV[3]
            local layout = ARGV[4]
            local currentLayout = redis.call('GET', KEYS[2])
            if currentLayout and currentLayout ~= layout then
                return -2
            end
            if not currentLayout then
                redis.call('SET', KEYS[2], layout)
            end
            for offset = 0, poolSize - 1 do
                local workerId = (cursor - 1 + offset) % poolSize
                local leaseKey = KEYS[3 + workerId]
                local acquired = redis.call('SET', leaseKey, owner, 'NX', 'PX', leaseMillis)
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

        String prefix = "{" + properties.getNamespace() + "}:worker";
        String cursorKey = prefix + ":cursor";
        String layoutKey = prefix + ":layout";
        List<String> leaseKeys = IntStream.range(0, properties.workerPoolSize())
                .mapToObj(workerId -> prefix + ":lease:" + workerId)
                .toList();

        Long acquiredWorkerId;
        long requestStartedNanos = nanoTime.getAsLong();
        try {
            acquiredWorkerId = redisService.executeLongScript(
                    ACQUIRE_SCRIPT,
                    Stream.concat(Stream.of(cursorKey, layoutKey), leaseKeys.stream())
                            .toList(),
                    List.of(properties.workerPoolSize(), leaseMillis, ownerToken,
                            layout(properties)));
        } catch (RuntimeException exception) {
            throw new IdGenerationException("无法从 Redis 获取 Worker ID 租约", exception);
        }
        if (Long.valueOf(ACQUIRE_LAYOUT_MISMATCH).equals(acquiredWorkerId)) {
            throw new IdGenerationException("当前命名空间已使用不同的 Worker ID 与序列位宽");
        }
        if (acquiredWorkerId == null || acquiredWorkerId < 0
                || acquiredWorkerId >= properties.workerPoolSize()) {
            throw new IdGenerationException("没有可用的 Worker ID 租约");
        }
        this.workerId = Math.toIntExact(acquiredWorkerId);
        this.leaseKey = leaseKeys.get(workerId);
        extendLocalSafetyPeriod(requestStartedNanos);
    }

    int workerId() {
        return workerId;
    }

    boolean isValid() {
        return active.get() && !closed.get() && nanoTime.getAsLong() - validUntilNanos.get() < 0;
    }

    void renew() {
        if (closed.get() || !active.get()) {
            return;
        }
        long requestStartedNanos = nanoTime.getAsLong();
        try {
            Long renewed = redisService.executeLongScript(
                    RENEW_SCRIPT,
                    List.of(leaseKey),
                    List.of(ownerToken, leaseMillis));
            if (Long.valueOf(RENEW_SUCCESS).equals(renewed)) {
                extendLocalSafetyPeriod(requestStartedNanos);
            } else {
                invalidate();
            }
        } catch (RuntimeException exception) {
            // 严格停发：无法确认租约仍归属当前实例时立即失效。
            log.warn("Worker ID 租约续租失败，立即停发 ID: leaseKey={}", leaseKey, exception);
            invalidate();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        invalidate();
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

    private void invalidate() {
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
