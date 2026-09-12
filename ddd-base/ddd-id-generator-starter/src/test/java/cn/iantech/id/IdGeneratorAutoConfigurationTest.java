package cn.iantech.id;

import cn.iantech.redis.IRedisService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdGeneratorAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdGeneratorAutoConfiguration.class));

    @Test
    void shouldCreateGeneratorAndBindProperties() {
        IRedisService redisService = successfulRedisService();

        contextRunner.withBean(IRedisService.class, () -> redisService)
                .withPropertyValues(
                        "ddd.id-generator.namespace=service-id",
                        "ddd.id-generator.worker-id-bit-length=9",
                        "ddd.id-generator.sequence-bit-length=13")
                .run(context -> {
                    assertThat(context).hasSingleBean(GlobalIdGenerator.class);
                    assertThat(context.getBean(GlobalIdGenerator.class).nextId()).isPositive();
                    assertThat(context.getBean(IdGeneratorProperties.class).getNamespace()).isEqualTo("service-id");
                });
    }

    @Test
    void shouldGenerateUniqueIdsConcurrently() {
        contextRunner.withBean(IRedisService.class, this::successfulRedisService)
                .run(context -> {
                    GlobalIdGenerator generator = context.getBean(GlobalIdGenerator.class);

                    Set<Long> ids = IntStream.range(0, 10_000)
                            .parallel()
                            .mapToObj(ignored -> generator.nextId())
                            .collect(Collectors.toSet());

                    assertThat(ids).hasSize(10_000);
                });
    }

    @Test
    void shouldNotCreateGeneratorWhenDisabled() {
        contextRunner.withPropertyValues("ddd.id-generator.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(GlobalIdGenerator.class));
    }

    @Test
    void shouldKeepUserProvidedGeneratorWithoutRedis() {
        GlobalIdGenerator custom = () -> 99L;

        contextRunner.withBean(GlobalIdGenerator.class, () -> custom)
                .run(context -> assertThat(context).hasSingleBean(GlobalIdGenerator.class)
                        .getBean(GlobalIdGenerator.class).isSameAs(custom));
    }

    @Test
    void shouldFailStartupWithoutRedisService() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldFailStartupForInvalidLeaseConfiguration() {
        contextRunner.withBean(IRedisService.class, this::successfulRedisService)
                .withPropertyValues(
                        "ddd.id-generator.lease-duration=10s",
                        "ddd.id-generator.renew-interval=10s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRegisterAutoConfigurationImports() throws IOException {
        String resourceName = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

        try (var input = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            assertThat(input).isNotNull();
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("cn.iantech.id.IdGeneratorAutoConfiguration");
        }
    }

    private IRedisService successfulRedisService() {
        IRedisService redisService = mock(IRedisService.class);
        when(redisService.executeLongScript(anyString(), anyList(), anyList()))
                .thenAnswer(invocation -> invocation.<String>getArgument(0).contains("INCR") ? 5L : 1L);
        return redisService;
    }
}
