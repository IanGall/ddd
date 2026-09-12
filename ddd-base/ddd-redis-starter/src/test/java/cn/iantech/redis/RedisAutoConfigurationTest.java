package cn.iantech.redis;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class));

    @Test
    void shouldCreateDefaultServiceWhenRedissonClientExists() {
        contextRunner.withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .run(context -> assertThat(context).hasSingleBean(IRedisService.class)
                        .getBean(IRedisService.class).isInstanceOf(RedissonRedisService.class));
    }

    @Test
    void shouldNotCreateServiceWithoutRedissonClient() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(IRedisService.class));
    }

    @Test
    void shouldKeepUserProvidedService() {
        IRedisService custom = mock(IRedisService.class);
        contextRunner.withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .withBean(IRedisService.class, () -> custom)
                .run(context -> assertThat(context).hasSingleBean(IRedisService.class)
                        .getBean(IRedisService.class).isSameAs(custom));
    }
}
