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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdGeneratorAutoConfigurationTest {

    private static final String[] BUSINESS_PROPERTIES = {
            "ddd.id-generator.worker-id-block-size=64",
            "ddd.id-generator.businesses.order=0",
            "ddd.id-generator.businesses.user=1",
            "ddd.id-generator.businesses.channel=2"};

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
                    assertThat(context).doesNotHaveBean(GlobalIdGeneratorProvider.class);
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
        IRedisService redisService = blockAwareRedisService(new CopyOnWriteArrayList<>());

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
    void shouldAssignDisjointWorkerIdsPerBusiness() {
        List<Integer> acquiredWorkerIds = new CopyOnWriteArrayList<>();
        IRedisService redisService = blockAwareRedisService(acquiredWorkerIds);

        contextRunner.withBean(IRedisService.class, () -> redisService)
                .withPropertyValues(BUSINESS_PROPERTIES)
                .run(context -> {
                    assertThat(acquiredWorkerIds).containsExactlyInAnyOrder(0, 64, 128);

                    GlobalIdGeneratorProvider provider = context.getBean(GlobalIdGeneratorProvider.class);
                    Set<Long> ids = Set.of(
                            provider.forBusiness("order").nextId(),
                            provider.forBusiness("user").nextId(),
                            provider.forBusiness("channel").nextId());

                    assertThat(ids).hasSize(3);
                });
    }

    @Test
    void shouldRejectUnknownBusiness() {
        contextRunner.withBean(IRedisService.class, () -> blockAwareRedisService(new CopyOnWriteArrayList<>()))
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
    void shouldFailStartupWhenBusinessRangesExceedWorkerPool() {
        contextRunner.withBean(IRedisService.class, () -> blockAwareRedisService(new CopyOnWriteArrayList<>()))
                .withPropertyValues(
                        "ddd.id-generator.worker-id-block-size=64",
                        "ddd.id-generator.businesses.order=0",
                        "ddd.id-generator.businesses.user=16")
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

    /**
     * 按 ARGV 中的区间返回该区间的第一个 Worker ID，模拟真实脚本的区间内扫描。
     */
    private IRedisService blockAwareRedisService(List<Integer> acquiredWorkerIds) {
        IRedisService redisService = mock(IRedisService.class);
        when(redisService.executeLongScript(anyString(), anyList(), anyList()))
                .thenAnswer(invocation -> {
                    if (!invocation.<String>getArgument(0).contains("INCR")) {
                        return 1L;
                    }
                    int rangeStart = (int) invocation.<List<?>>getArgument(2).get(0);
                    acquiredWorkerIds.add(rangeStart);
                    return (long) rangeStart;
                });
        return redisService;
    }
}
