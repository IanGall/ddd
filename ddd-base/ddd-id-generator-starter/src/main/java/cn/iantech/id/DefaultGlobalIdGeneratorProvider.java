package cn.iantech.id;

import cn.iantech.redis.IRedisService;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 启动时按业务预建生成器的 Provider。
 *
 * <p>业务生成器在构造阶段一次性建好：任一业务拿不到 Worker ID 租约都会让启动失败，
 * 而不是等到第一次出号才暴露。所有业务共享同一个续租调度线程。
 */
final class DefaultGlobalIdGeneratorProvider implements GlobalIdGeneratorProvider {

    private static final Comparator<Map.Entry<String, Integer>> BY_BLOCK_INDEX =
            Comparator.comparingInt(Map.Entry::getValue);

    private final Map<String, LeaseBackedGlobalIdGenerator> generators;
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
        try {
            // 按块序号升序建号，保证启动期租用 Worker ID 的顺序稳定可预期。
            properties.getBusinesses().entrySet().stream()
                    .sorted(BY_BLOCK_INDEX)
                    .forEach(entry -> generators.put(entry.getKey(),
                            create(redisService, properties, scheduler, entry.getValue())));
        } catch (RuntimeException exception) {
            generators.values().forEach(LeaseBackedGlobalIdGenerator::close);
            scheduler.shutdownNow();
            throw exception;
        }
    }

    @Override
    public GlobalIdGenerator forBusiness(String business) {
        LeaseBackedGlobalIdGenerator generator = generators.get(business);
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
        generators.values().forEach(LeaseBackedGlobalIdGenerator::close);
        scheduler.shutdownNow();
    }

    private static LeaseBackedGlobalIdGenerator create(IRedisService redisService,
                                                       IdGeneratorProperties properties,
                                                       ScheduledExecutorService scheduler,
                                                       int businessIndex) {
        RedisWorkerLease workerLease = RedisWorkerLease.forBlock(redisService, properties, businessIndex);
        return new LeaseBackedGlobalIdGenerator(workerLease,
                SnowflakeIdGenerator.of(workerLease.workerId(), properties),
                scheduler, properties.getRenewInterval().toMillis());
    }
}
