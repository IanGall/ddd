package cn.iantech.id.autoconfigure;

import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.id.GlobalIdGeneratorProvider;
import cn.iantech.id.IdGenerationException;
import cn.iantech.redis.IRedisService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdGeneratorAutoConfigurationTest {

    private static final String APPLICATION_NAME = "id-generator-test";

    /** 叶子值是该业务的序列位宽。 */
    private static final String[] BUSINESS_PROPERTIES = {
            "ddd.id-generator.businesses.order=12",
            "ddd.id-generator.businesses.user=12",
            "ddd.id-generator.businesses.channel=12"};

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdGeneratorAutoConfiguration.class))
            .withPropertyValues("spring.application.name=" + APPLICATION_NAME);

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
                    assertThat(context).doesNotHaveBean(GlobalIdGeneratorProvider.class);
                    assertThat(context.getBean(GlobalIdGenerator.class).nextId()).isPositive();
                    assertThat(context.getBean(IdGeneratorProperties.class).getNamespace()).isEqualTo("service-id");
                });
    }

    @Test
    void shouldFailStartupWhenNamespaceCannotBeResolved() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdGeneratorAutoConfiguration.class))
                .withBean(IRedisService.class, this::successfulRedisService)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IdGenerationException.class);
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
        contextRunner.withPropertyValues(businessProperties("ddd.id-generator.enabled=false"))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(GlobalIdGenerator.class);
                    assertThat(context).doesNotHaveBean(GlobalIdGeneratorProvider.class);
                });
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
    void shouldCreateOnlyProviderWhenBusinessesAreConfigured() {
        IRedisService redisService = successfulRedisService();

        contextRunner.withBean(IRedisService.class, () -> redisService)
                .withPropertyValues(BUSINESS_PROPERTIES)
                .run(context -> {
                    assertThat(context).hasSingleBean(GlobalIdGeneratorProvider.class);
                    assertThat(context).doesNotHaveBean(GlobalIdGenerator.class);

                    GlobalIdGeneratorProvider provider = context.getBean(GlobalIdGeneratorProvider.class);
                    List<String> declared = List.of("order", "user", "channel");
                    assertThat(declared).allSatisfy(business -> assertThat(provider.forBusiness(business)).isNotNull());
                    assertThat(provider.forBusiness("order")).isSameAs(provider.forBusiness("order"));
                    assertThat(provider.forBusiness("order").nextId()).isPositive();
                });
    }

    @Test
    void shouldLeaseExactlyOnceForAllBusinesses() {
        IRedisService redisService = successfulRedisService();

        contextRunner.withBean(IRedisService.class, () -> redisService)
                .withPropertyValues(BUSINESS_PROPERTIES)
                .run(context -> {
                    GlobalIdGeneratorProvider provider = context.getBean(GlobalIdGeneratorProvider.class);

                    // Worker ID 是实例级资源：三个业务共用一个租约，只取一次
                    verify(redisService, times(1)).executeLongScript(anyString(), anyList(), anyList());

                    // 同一业务内的 ID 必须互不重复（这是唯一要保证的「业务域内唯一」）
                    Set<Long> withinOneBusiness = IntStream.range(0, 1_000)
                            .mapToObj(ignored -> provider.forBusiness("order").nextId())
                            .collect(Collectors.toSet());
                    assertThat(withinOneBusiness).hasSize(1_000);
                });
    }

    @Test
    void shouldRejectUnknownBusiness() {
        contextRunner.withBean(IRedisService.class, this::successfulRedisService)
                .withPropertyValues(BUSINESS_PROPERTIES)
                .run(context -> {
                    GlobalIdGeneratorProvider provider = context.getBean(GlobalIdGeneratorProvider.class);

                    assertThatThrownBy(() -> provider.forBusiness("unknown"))
                            .isInstanceOf(IdGenerationException.class)
                            .hasMessageContaining("未声明的业务 ID 生成器：unknown")
                            .hasMessageContaining("order");
                });
    }

    @Test
    void shouldKeepUserProvidedProviderWithoutRedis() {
        GlobalIdGenerator custom = () -> 99L;
        GlobalIdGeneratorProvider provider = new GlobalIdGeneratorProvider() {
            @Override
            public GlobalIdGenerator forBusiness(String business) {
                return custom;
            }

            @Override
            public void close() {
            }
        };

        contextRunner.withBean(GlobalIdGeneratorProvider.class, () -> provider)
                .withPropertyValues(BUSINESS_PROPERTIES)
                .run(context -> assertThat(context).hasSingleBean(GlobalIdGeneratorProvider.class)
                        .getBean(GlobalIdGeneratorProvider.class).isSameAs(provider));
    }

    @Test
    void shouldFailStartupWhenWorkerAndSequenceBitsExceedLimit() {
        contextRunner.withBean(IRedisService.class, this::successfulRedisService)
                .withPropertyValues(
                        "ddd.id-generator.worker-id-bit-length=14",
                        "ddd.id-generator.businesses.order=12")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRegisterAutoConfigurationImports() throws IOException {
        String resourceName = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

        try (var input = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            assertThat(input).isNotNull();
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("cn.iantech.id.autoconfigure.IdGeneratorAutoConfiguration");
        }
    }

    private static String[] businessProperties(String... extra) {
        return Stream.concat(Arrays.stream(extra), Arrays.stream(BUSINESS_PROPERTIES)).toArray(String[]::new);
    }

    private IRedisService successfulRedisService() {
        IRedisService redisService = mock(IRedisService.class);
        when(redisService.executeLongScript(anyString(), anyList(), anyList()))
                .thenAnswer(invocation -> invocation.<String>getArgument(0).contains("INCR") ? 5L : 1L);
        return redisService;
    }
}
