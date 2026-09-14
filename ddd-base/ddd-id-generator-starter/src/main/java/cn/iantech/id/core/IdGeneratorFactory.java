package cn.iantech.id.core;

import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.id.GlobalIdGeneratorProvider;
import cn.iantech.id.autoconfigure.IdGeneratorProperties;
import cn.iantech.redis.IRedisService;

import java.util.Objects;
import java.util.UUID;

/**
 * ID 生成器的唯一构造入口。
 *
 * <p>两种模式都要求调用方先提供 {@link IdGeneratorProperties}；本类负责校验配置并组装
 * 「Worker ID 租约 + 算法 + 续租」三件套，使 {@link SnowflakeIdGenerator}、{@link RedisWorkerLease}
 * 等实现细节保持包私有，不进入对外契约。
 */
public final class IdGeneratorFactory {

    private IdGeneratorFactory() {
    }

    /**
     * 单生成器模式：WorkerId 从整个池租用，适用于未声明 {@code ddd.id-generator.businesses} 的服务。
     *
     * @param redisService Redis 服务，用于租用 WorkerId
     * @param properties   ID 生成器配置
     * @return 全局唯一 ID 生成器，调用方负责在销毁时关闭
     * @throws cn.iantech.id.IdGenerationException 配置非法或租不到 WorkerId 时抛出
     */
    public static GlobalIdGenerator single(IRedisService redisService, IdGeneratorProperties properties) {
        Objects.requireNonNull(redisService, "Redis 服务不能为空");
        Objects.requireNonNull(properties, "ID 生成器配置不能为空").validate();

        RedisWorkerLease workerLease = new RedisWorkerLease(redisService, properties,
                System::nanoTime, UUID.randomUUID().toString());
        return LeaseBackedGlobalIdGenerator.withOwnScheduler(workerLease,
                SnowflakeIdGenerator.of(workerLease.workerId(), properties),
                properties.getRenewInterval().toMillis());
    }

    /**
     * 多业务模式：每个已声明业务独占一段 Worker ID 区间，启动期一次性建好全部生成器。
     *
     * @param redisService Redis 服务，用于租用 WorkerId
     * @param properties   ID 生成器配置，必须已声明 {@code businesses}
     * @return 按业务取生成器的 Provider，调用方负责在销毁时关闭
     * @throws cn.iantech.id.IdGenerationException 配置非法或任一业务租不到 WorkerId 时抛出
     */
    public static GlobalIdGeneratorProvider perBusiness(IRedisService redisService,
                                                       IdGeneratorProperties properties) {
        return new DefaultGlobalIdGeneratorProvider(redisService, properties);
    }
}
