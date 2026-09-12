package cn.iantech.id;

import cn.iantech.redis.IRedisService;
import cn.iantech.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 全局 ID 生成器自动装配。
 */
@AutoConfiguration
@AutoConfigureAfter(RedisAutoConfiguration.class)
@EnableConfigurationProperties(IdGeneratorProperties.class)
@ConditionalOnProperty(prefix = "ddd.id-generator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IdGeneratorAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(GlobalIdGenerator.class)
    public GlobalIdGenerator globalIdGenerator(IRedisService redisService, IdGeneratorProperties properties) {
        properties.validate();
        RedisWorkerLease workerLease = new RedisWorkerLease(redisService, properties);
        return new YitterGlobalIdGenerator(workerLease, properties);
    }
}
