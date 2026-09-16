package cn.iantech.id.core;

import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.id.GlobalIdGeneratorProvider;
import cn.iantech.id.IdGenerationException;
import cn.iantech.id.autoconfigure.IdGeneratorProperties;
import cn.iantech.redis.IRedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class IdGeneratorFactoryTest {

    private IRedisService redisService;
    private IdGeneratorProperties properties;

    @BeforeEach
    void setUp() {
        redisService = mock(IRedisService.class);
        when(redisService.executeLongScript(anyString(), anyList(), anyList()))
                .thenAnswer(invocation -> invocation.<String>getArgument(0).contains("INCR") ? 5L : 1L);
        properties = new IdGeneratorProperties();
        properties.setNamespace("ddd-global-id");
    }

    @Test
    void shouldBuildSingleGeneratorLeasingFromWholePool() {
        GlobalIdGenerator generator = IdGeneratorFactory.single(redisService, properties);
        try {
            assertThat(generator.nextId()).isPositive();

            ArgumentCaptor<List<?>> argumentsCaptor = ArgumentCaptor.forClass(List.class);
            verify(redisService).executeLongScript(anyString(), anyList(), argumentsCaptor.capture());
            assertThat(argumentsCaptor.getValue().get(0)).isEqualTo(properties.workerPoolSize());
            assertThat(argumentsCaptor.getValue().get(3)).isEqualTo("10");
            assertThat(argumentsCaptor.getValue().get(4)).isEqualTo(0);
        } finally {
            close(generator);
        }
    }

    @Test
    void shouldBuildProviderResolvingDeclaredBusinessAndRejectingUnknownOne() {
        properties.setBusinesses(Map.of("order", 12));

        GlobalIdGeneratorProvider provider = IdGeneratorFactory.perBusiness(redisService, properties);
        try {
            GlobalIdGenerator orderGenerator = provider.forBusiness("order");
            assertThat(orderGenerator.nextId()).isPositive();
            assertThat(provider.forBusiness("order")).isSameAs(orderGenerator);

            // 全部业务共用一个实例级 Worker ID：只应有一次取租约调用
            ArgumentCaptor<List<?>> argumentsCaptor = ArgumentCaptor.forClass(List.class);
            verify(redisService, times(1)).executeLongScript(anyString(), anyList(), argumentsCaptor.capture());
            assertThat(argumentsCaptor.getValue().get(0)).isEqualTo(properties.workerPoolSize());
            assertThat(argumentsCaptor.getValue().get(4)).isEqualTo(1);

            assertThatThrownBy(() -> provider.forBusiness("unknown"))
                    .isInstanceOf(IdGenerationException.class)
                    .hasMessageContaining("未声明的业务 ID 生成器：unknown");
        } finally {
            provider.close();
        }
    }

    @Test
    void shouldRejectInvalidPropertiesOnBothModes() {
        properties.setLeaseDuration(Duration.ofSeconds(10));
        properties.setRenewInterval(Duration.ofSeconds(10));

        assertThatThrownBy(() -> IdGeneratorFactory.single(redisService, properties))
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("renew-interval");
        assertThatThrownBy(() -> IdGeneratorFactory.perBusiness(redisService, properties))
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("renew-interval");
        verifyNoInteractions(redisService);
    }

    @Test
    void shouldRejectMissingArguments() {
        assertThatThrownBy(() -> IdGeneratorFactory.single(null, properties))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> IdGeneratorFactory.single(redisService, null))
                .isInstanceOf(NullPointerException.class);
    }

    private void close(GlobalIdGenerator generator) {
        try {
            ((AutoCloseable) generator).close();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
