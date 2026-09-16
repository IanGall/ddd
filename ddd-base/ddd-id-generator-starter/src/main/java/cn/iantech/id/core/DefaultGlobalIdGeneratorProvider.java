package cn.iantech.id.core;

import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.id.GlobalIdGeneratorProvider;
import cn.iantech.id.IdGenerationException;
import cn.iantech.id.autoconfigure.IdGeneratorProperties;
import cn.iantech.redis.IRedisService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 启动时按业务预建生成器的 Provider。
 *
 * <p>全部业务共用同一个**实例级** Worker ID 租约，因此也共用同一条续租任务与同一个失效开关：
 * 租约一旦失效，本实例内所有业务同时停发。
 *
 * <p>每个业务仍持有独立的生成器实例（独立锁、独立序列计数器）与独立的序列位宽，
 * 因此业务数量不受 Worker ID 池容量限制。任一业务配置非法或租不到 Worker ID 都会整体启动失败，
 * 而不是等到第一次出号才暴露。
 */
final class DefaultGlobalIdGeneratorProvider implements GlobalIdGeneratorProvider {

    private final Map<String, GlobalIdGenerator> generators;
    private final WorkerIdLeaseHolder leaseHolder;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean closed = new AtomicBoolean();

    DefaultGlobalIdGeneratorProvider(IRedisService redisService, IdGeneratorProperties properties) {
        this(redisService, properties, RenewalScheduler.singleDaemonThread());
    }

    DefaultGlobalIdGeneratorProvider(IRedisService redisService, IdGeneratorProperties properties,
                                     ScheduledExecutorService scheduler) {
        Objects.requireNonNull(redisService, "Redis 服务不能为空");
        Objects.requireNonNull(properties, "ID 生成器配置不能为空").validate();
        this.scheduler = Objects.requireNonNull(scheduler, "续租调度器不能为空");
        this.generators = new LinkedHashMap<>();

        WorkerIdLeaseHolder holder = null;
        try {
            holder = new WorkerIdLeaseHolder(
                    new RedisWorkerLease(redisService, properties, System::nanoTime, UUID.randomUUID().toString()),
                    scheduler, properties.getRenewInterval().toMillis());
            // 按业务名升序建号，保证启动期构建顺序稳定可预期。
            for (Map.Entry<String, Integer> entry : new TreeMap<>(properties.getBusinesses()).entrySet()) {
                String business = entry.getKey();
                generators.put(business, holder.bind(SnowflakeIdGenerator.of(holder.workerId(),
                        properties.getWorkerIdBitLength(), properties.sequenceBitLengthFor(business))));
            }
            this.leaseHolder = holder;
        } catch (RuntimeException exception) {
            if (holder != null) {
                holder.close();
            }
            scheduler.shutdownNow();
            throw exception;
        }
    }

    @Override
    public GlobalIdGenerator forBusiness(String business) {
        GlobalIdGenerator generator = generators.get(business);
        if (generator == null) {
            throw new IdGenerationException(
                    "未声明的业务 ID 生成器：" + business + "，已声明的业务：" + generators.keySet());
        }
        return generator;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        leaseHolder.close();
        scheduler.shutdownNow();
    }
}
