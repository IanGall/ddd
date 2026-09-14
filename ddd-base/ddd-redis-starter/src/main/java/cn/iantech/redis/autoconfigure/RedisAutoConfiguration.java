package cn.iantech.redis.autoconfigure;

import cn.iantech.redis.IRedisService;
import cn.iantech.redis.RedissonRedisService;
import org.redisson.api.RedissonClient;
import org.redisson.spring.starter.RedissonAutoConfigurationV4;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 公共 Redis 服务自动装配。
 */
@AutoConfiguration
@AutoConfigureAfter(RedissonAutoConfigurationV4.class)
@ConditionalOnClass(RedissonClient.class)
public class RedisAutoConfiguration {

    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean(IRedisService.class)
    public IRedisService redisService(RedissonClient redissonClient) {
        return new RedissonRedisService(redissonClient);
    }
}
