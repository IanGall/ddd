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
 *
 * <p>两种模式的差别只在生成器实例数量：单生成器模式一个实例，多业务模式每个业务一个实例。
 * 两者都从服务级池中租用**一个**实例级 Worker ID。
 */
public final class IdGeneratorFactory {

    private IdGeneratorFactory() {
    }

    /**
     * 单生成器模式：整个进程共用一个生成器实例，适用于未声明 {@code ddd.id-generator.businesses} 的服务。
     *
     * @param redisService Redis 服务，用于租用 WorkerId
     * @param properties   ID 生成器配置
     * @return 绑定实例级 Worker ID 租约的生成器，调用方负责在销毁时关闭
     * @throws cn.iantech.id.IdGenerationException 配置非法或租不到 WorkerId 时抛出
     */
    public static GlobalIdGenerator single(IRedisService redisService, IdGeneratorProperties properties) {
        Objects.requireNonNull(redisService, "Redis 服务不能为空");
        Objects.requireNonNull(properties, "ID 生成器配置不能为空").validate();

        WorkerIdLeaseHolder holder = WorkerIdLeaseHolder.withOwnScheduler(
                new RedisWorkerLease(redisService, properties, System::nanoTime, UUID.randomUUID().toString()),
                properties.getRenewInterval().toMillis());
        try {
            return holder.bind(SnowflakeIdGenerator.of(holder.workerId(),
                    properties.getWorkerIdBitLength(), properties.getSequenceBitLength()));
        } catch (RuntimeException exception) {
            holder.close();
            throw exception;
        }
    }

    /**
     * 多业务模式：每个已声明业务独占一个生成器实例，全部业务共用同一个实例级 Worker ID
     * 与同一条租约，启动期一次性建好。
     *
     * @param redisService Redis 服务，用于租用 WorkerId
     * @param properties   ID 生成器配置，必须已声明 {@code businesses}
     * @return 按业务取生成器的 Provider，调用方负责在销毁时关闭
     * @throws cn.iantech.id.IdGenerationException 配置非法或租不到 WorkerId 时抛出
     */
    public static GlobalIdGeneratorProvider perBusiness(IRedisService redisService,
                                                       IdGeneratorProperties properties) {
        return new DefaultGlobalIdGeneratorProvider(redisService, properties);
    }
}
