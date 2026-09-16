package cn.iantech.id.autoconfigure;

import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.id.GlobalIdGeneratorProvider;
import cn.iantech.id.IdGenerationException;
import cn.iantech.id.core.IdGeneratorFactory;
import cn.iantech.redis.IRedisService;
import cn.iantech.redis.autoconfigure.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;

/**
 * 全局 ID 生成器自动装配。
 *
 * <p>Worker ID 池是**服务级**的：命名空间即隔离边界。{@code ddd.id-generator.namespace}
 * 未显式配置时从 {@code spring.application.name} 派生，使不同服务天然落在各自的池里，
 * 避免所有服务共用一个池而互相抢占 Worker ID。两者都取不到时启动失败，
 * 而不是退化到一个共享的默认命名空间。
 *
 * <p>声明了 {@code ddd.id-generator.businesses} 时只提供 {@link GlobalIdGeneratorProvider}，
 * 由调用方按业务取生成器；未声明时保持单个 {@link GlobalIdGenerator} 的兼容模式。
 * 两种模式都只租用**一个**实例级 Worker ID。
 */
@AutoConfiguration
@AutoConfigureAfter(RedisAutoConfiguration.class)
@EnableConfigurationProperties(IdGeneratorProperties.class)
@ConditionalOnProperty(prefix = "ddd.id-generator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IdGeneratorAutoConfiguration {

    @Bean(destroyMethod = "close")
    @Conditional(BusinessesConfiguredCondition.class)
    @ConditionalOnMissingBean(GlobalIdGeneratorProvider.class)
    public GlobalIdGeneratorProvider globalIdGeneratorProvider(IRedisService redisService,
                                                              IdGeneratorProperties properties,
                                                              Environment environment) {
        properties.setNamespace(resolveNamespace(properties, environment));
        return IdGeneratorFactory.perBusiness(redisService, properties);
    }

    @Bean(destroyMethod = "close")
    @Conditional(NoBusinessesConfiguredCondition.class)
    @ConditionalOnMissingBean(GlobalIdGenerator.class)
    public GlobalIdGenerator globalIdGenerator(IRedisService redisService, IdGeneratorProperties properties,
                                               Environment environment) {
        properties.setNamespace(resolveNamespace(properties, environment));
        return IdGeneratorFactory.single(redisService, properties);
    }

    /**
     * 解析服务级命名空间，并把结果写回配置对象，使后续的 {@code validate()} 能一同校验。
     */
    private static String resolveNamespace(IdGeneratorProperties properties, Environment environment) {
        String configured = properties.getNamespace();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String applicationName = environment.getProperty("spring.application.name");
        if (applicationName == null || applicationName.isBlank()) {
            throw new IdGenerationException(
                    "无法确定 ID 生成器命名空间：请配置 ddd.id-generator.namespace 或 spring.application.name");
        }
        return applicationName;
    }
}
