package cn.iantech.id;

import cn.iantech.redis.IRedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RedisWorkerLeaseTest {

    private IRedisService redisService;
    private IdGeneratorProperties properties;
    private AtomicLong nanoTime;

    @BeforeEach
    void setUp() {
        redisService = mock(IRedisService.class);
        properties = new IdGeneratorProperties();
        properties.setLeaseDuration(Duration.ofMillis(100));
        properties.setRenewInterval(Duration.ofMillis(30));
        nanoTime = new AtomicLong(1_000L);
    }

    @Test
    void shouldAcquireWorkerFromConfiguredPoolAndUseSameHashTag() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList())).thenReturn(17L);

        RedisWorkerLease lease = new RedisWorkerLease(redisService, properties, nanoTime::get, "instance-a");

        assertThat(lease.workerId()).isEqualTo(17);
        assertThat(lease.isValid()).isTrue();

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<?>> argumentsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> scriptCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisService).executeLongScript(
                scriptCaptor.capture(), keysCaptor.capture(), argumentsCaptor.capture());
        assertThat(scriptCaptor.getValue()).contains("'SET', leaseKey, owner, 'NX', 'PX', leaseMillis");
        assertThat(keysCaptor.getValue()).hasSize(1026)
                .allMatch(key -> key.startsWith("{ddd-global-id}:worker:"));
        assertThat(keysCaptor.getValue()).contains(
                "{ddd-global-id}:worker:cursor",
                "{ddd-global-id}:worker:layout",
                "{ddd-global-id}:worker:lease:0",
                "{ddd-global-id}:worker:lease:1023");
        assertThat(argumentsCaptor.getValue()).isEqualTo(List.of(1024, 100L, "instance-a", "10:12"));
    }

    @Test
    void shouldRenewAndReleaseRedisTtlLeaseOnlyForCurrentOwner() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList()))
                .thenReturn(3L, 1L, 1L);
        RedisWorkerLease lease = new RedisWorkerLease(redisService, properties, nanoTime::get, "instance-a");

        lease.renew();
        lease.close();

        ArgumentCaptor<String> scriptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<?>> argumentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisService, times(3)).executeLongScript(
                scriptCaptor.capture(), keysCaptor.capture(), argumentsCaptor.capture());
        assertThat(scriptCaptor.getAllValues().get(1)).contains("'GET', KEYS[1]", "'PEXPIRE'");
        assertThat(scriptCaptor.getAllValues().get(2)).contains("'GET', KEYS[1]", "'DEL'");
        assertThat(keysCaptor.getAllValues().get(1))
                .containsExactly("{ddd-global-id}:worker:lease:3");
        assertThat(argumentsCaptor.getAllValues().get(1)).isEqualTo(List.of("instance-a", 100L));
        assertThat(argumentsCaptor.getAllValues().get(2)).isEqualTo(List.of("instance-a"));
    }

    @Test
    void shouldFailWhenWorkerPoolIsExhausted() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList())).thenReturn(-1L);

        assertThatThrownBy(() -> new RedisWorkerLease(
                redisService, properties, nanoTime::get, "instance-a"))
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("没有可用");
    }

    @Test
    void shouldFailWhenNamespaceLayoutDoesNotMatch() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList())).thenReturn(-2L);

        assertThatThrownBy(() -> new RedisWorkerLease(
                redisService, properties, nanoTime::get, "instance-a"))
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("不同的 Worker ID 与序列位宽");
    }

    @Test
    void shouldExtendMonotonicSafetyPeriodAfterSuccessfulRenewal() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList()))
                .thenReturn(3L, 1L);
        RedisWorkerLease lease = new RedisWorkerLease(redisService, properties, nanoTime::get, "instance-a");
        nanoTime.set(Duration.ofMillis(60).toNanos());

        lease.renew();
        nanoTime.set(Duration.ofMillis(120).toNanos());

        assertThat(lease.isValid()).isTrue();
    }

    @Test
    void shouldDeductRedisRoundTripTimeFromLocalSafetyPeriod() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList()))
                .thenReturn(3L)
                .thenAnswer(invocation -> {
                    nanoTime.set(Duration.ofMillis(120).toNanos());
                    return 1L;
                });
        RedisWorkerLease lease = new RedisWorkerLease(redisService, properties, nanoTime::get, "instance-a");
        nanoTime.set(Duration.ofMillis(60).toNanos());

        lease.renew();
        nanoTime.set(Duration.ofMillis(131).toNanos());

        assertThat(lease.isValid()).isFalse();
    }

    @Test
    void shouldInvalidateImmediatelyOnRedisFailure() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList()))
                .thenReturn(3L)
                .thenThrow(new IllegalStateException("Redis unavailable"));
        RedisWorkerLease lease = new RedisWorkerLease(redisService, properties, nanoTime::get, "instance-a");

        lease.renew();

        assertThat(lease.isValid()).isFalse();
    }

    @Test
    void shouldInvalidateImmediatelyWhenRedisRejectsRenewal() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList()))
                .thenReturn(3L, 0L);
        RedisWorkerLease lease = new RedisWorkerLease(redisService, properties, nanoTime::get, "instance-a");

        lease.renew();

        assertThat(lease.isValid()).isFalse();
    }

    @Test
    void shouldExpireUsingMonotonicClockAndReleaseOnlyOnce() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList())).thenReturn(3L, 1L);
        RedisWorkerLease lease = new RedisWorkerLease(redisService, properties, nanoTime::get, "instance-a");
        nanoTime.addAndGet(Duration.ofMillis(71).toNanos());

        assertThat(lease.isValid()).isFalse();

        lease.close();
        lease.close();

        assertThat(lease.isValid()).isFalse();
        verify(redisService, times(2)).executeLongScript(anyString(), anyList(), anyList());
    }
}
