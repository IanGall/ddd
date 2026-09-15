package cn.iantech.id.core;

import cn.iantech.id.IdGenerationException;
import cn.iantech.id.autoconfigure.IdGeneratorProperties;
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
        assertThat(scriptCaptor.getValue()).contains("'SET', KEYS[3 + slot], owner, 'NX', 'PX', leaseMillis");
        assertThat(keysCaptor.getValue()).hasSize(1026)
                .allMatch(key -> key.startsWith("{ddd-global-id}:worker:"));
        assertThat(keysCaptor.getValue()).contains(
                "{ddd-global-id}:worker:cursor",
                "{ddd-global-id}:worker:layout",
                "{ddd-global-id}:worker:lease:0",
                "{ddd-global-id}:worker:lease:1023");
        assertThat(argumentsCaptor.getValue()).isEqualTo(List.of(0, 1024, 100L, "instance-a", "10:12"));
    }

    @Test
    void shouldAcquireWithinBusinessBlockAndUseBlockCursorKey() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList())).thenReturn(130L);

        RedisWorkerLease lease = RedisWorkerLease.forBlock(redisService, properties, 2);

        assertThat(lease.workerId()).isEqualTo(130);

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<?>> argumentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisService).executeLongScript(anyString(), keysCaptor.capture(), argumentsCaptor.capture());
        assertThat(keysCaptor.getValue()).hasSize(66)
                .startsWith("{ddd-global-id}:worker:cursor:2", "{ddd-global-id}:worker:layout",
                        "{ddd-global-id}:worker:lease:128")
                .endsWith("{ddd-global-id}:worker:lease:191");
        assertThat(argumentsCaptor.getValue().get(0)).isEqualTo(128);
        assertThat(argumentsCaptor.getValue().get(1)).isEqualTo(64);
        assertThat(argumentsCaptor.getValue().get(2)).isEqualTo(100L);
        assertThat(argumentsCaptor.getValue().get(3).toString()).isNotBlank();
        assertThat(argumentsCaptor.getValue().get(4)).isEqualTo("10:12");
    }

    @Test
    void shouldRejectWorkerIdReturnedOutsideBusinessBlock() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList())).thenReturn(192L);

        assertThatThrownBy(() -> RedisWorkerLease.forBlock(redisService, properties, 2))
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("区间外的 Worker ID：192")
                .hasMessageContaining("[128, 192)");
    }

    @Test
    void shouldFailWithBlockRangeWhenBusinessBlockIsExhausted() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList())).thenReturn(-1L);

        assertThatThrownBy(() -> RedisWorkerLease.forBlock(redisService, properties, 1))
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("没有可用")
                .hasMessageContaining("[64, 128)");
    }

    @Test
    void shouldShareLayoutKeyAcrossBusinessBlocks() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList())).thenReturn(0L, 64L);

        RedisWorkerLease.forBlock(redisService, properties, 0);
        RedisWorkerLease.forBlock(redisService, properties, 1);

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<?>> argumentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisService, times(2)).executeLongScript(anyString(), keysCaptor.capture(), argumentsCaptor.capture());
        assertThat(keysCaptor.getAllValues()).allSatisfy(
                keys -> assertThat(keys).contains("{ddd-global-id}:worker:layout"));
        assertThat(keysCaptor.getAllValues().get(0)).doesNotContain("{ddd-global-id}:worker:cursor:1");
        assertThat(argumentsCaptor.getAllValues().get(0).get(4)).isEqualTo("10:12");
        assertThat(argumentsCaptor.getAllValues().get(1).get(4)).isEqualTo("10:12");
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
    void shouldReacquireSameWorkerIdWhenLeaseKeyDisappears() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList()))
                .thenReturn(3L, 0L, 1L);
        RedisWorkerLease lease = new RedisWorkerLease(redisService, properties, nanoTime::get, "instance-a");
        nanoTime.set(Duration.ofMillis(200).toNanos());
        assertThat(lease.isValid()).isFalse();

        lease.renew();

        assertThat(lease.isValid()).isTrue();
        assertThat(lease.workerId()).isEqualTo(3);

        ArgumentCaptor<String> scriptCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisService, times(3)).executeLongScript(scriptCaptor.capture(), anyList(), anyList());
        assertThat(scriptCaptor.getAllValues().get(2))
                .as("重取租约必须是针对本实例 leaseKey 的原子 SET NX")
                .contains("redis.call('SET', KEYS[1], owner, 'NX', 'PX', leaseMillis)");
    }

    @Test
    void shouldRecoverOnNextRenewalAfterTransientRedisFailure() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList()))
                .thenReturn(3L)
                .thenThrow(new IllegalStateException("Redis unavailable"))
                .thenReturn(1L);
        RedisWorkerLease lease = new RedisWorkerLease(redisService, properties, nanoTime::get, "instance-a");
        nanoTime.set(Duration.ofMillis(200).toNanos());

        lease.renew();
        assertThat(lease.isValid()).isFalse();

        // Redis 恢复后，下一个续租周期必须能够自愈，而不是永久停发
        nanoTime.set(Duration.ofMillis(230).toNanos());
        lease.renew();

        assertThat(lease.isValid()).isTrue();
    }

    @Test
    void shouldStaySuspendedWhileAnotherInstanceHoldsTheLease() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList()))
                .thenReturn(3L, 0L, 0L);
        RedisWorkerLease lease = new RedisWorkerLease(redisService, properties, nanoTime::get, "instance-a");
        nanoTime.set(Duration.ofMillis(200).toNanos());

        lease.renew();

        assertThat(lease.isValid()).isFalse();
    }

    @Test
    void shouldNotRenewAfterClose() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList()))
                .thenReturn(3L, 1L);
        RedisWorkerLease lease = new RedisWorkerLease(redisService, properties, nanoTime::get, "instance-a");

        lease.close();
        nanoTime.set(Duration.ofMillis(200).toNanos());
        lease.renew();

        assertThat(lease.isValid()).isFalse();
        // 只应有 acquire 与 release 两次 Redis 调用，关闭后不得再发起续租
        verify(redisService, times(2)).executeLongScript(anyString(), anyList(), anyList());
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
