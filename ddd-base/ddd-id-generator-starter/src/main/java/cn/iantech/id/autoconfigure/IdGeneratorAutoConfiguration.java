package cn.iantech.id.autoconfigure;

import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.id.GlobalIdGeneratorProvider;
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

/**
 * 全局 ID 生成器自动装配。
 *
 * <p>声明了 {@code ddd.id-generator.businesses} 时只提供 {@link GlobalIdGeneratorProvider}，
 * 由调用方按业务取生成器；未声明时保持单个 {@link GlobalIdGenerator} 的兼容模式。
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
                                                              IdGeneratorProperties properties) {
        return IdGeneratorFactory.perBusiness(redisService, properties);
    }

    @Bean(destroyMethod = "close")
    @Conditional(NoBusinessesConfiguredCondition.class)
    @ConditionalOnMissingBean(GlobalIdGenerator.class)
    public GlobalIdGenerator globalIdGenerator(IRedisService redisService, IdGeneratorProperties properties) {
        return IdGeneratorFactory.single(redisService, properties);
    }
}
