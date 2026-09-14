package cn.iantech.id;

import cn.iantech.redis.IRedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LeaseBackedGlobalIdGeneratorTest {

    private static final long RENEW_INTERVAL_MILLIS = 30L;

    private IRedisService redisService;
    private IdGeneratorProperties properties;
    private AtomicLong nanoTime;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> renewalTask;

    @BeforeEach
    void setUp() {
        redisService = mock(IRedisService.class);
        properties = new IdGeneratorProperties();
        properties.setLeaseDuration(Duration.ofMillis(100));
        properties.setRenewInterval(Duration.ofMillis(30));
        nanoTime = new AtomicLong(1_000L);
        renewalTask = mock(ScheduledFuture.class);
        scheduler = mock(ScheduledExecutorService.class);
        doReturn(renewalTask).when(scheduler)
                .scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void shouldDelegateGenerationWhileLeaseIsValid() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList())).thenReturn(7L, 1L);
        LeaseBackedGlobalIdGenerator generator = generator();

        assertThat(generator.nextId()).isEqualTo(42L);

        verify(scheduler).scheduleWithFixedDelay(any(Runnable.class),
                eq(RENEW_INTERVAL_MILLIS), eq(RENEW_INTERVAL_MILLIS), eq(TimeUnit.MILLISECONDS));

        generator.close();

        verify(renewalTask).cancel(false);
    }

    @Test
    void shouldFailClosedAfterLeaseExpires() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList())).thenReturn(7L);
        LeaseBackedGlobalIdGenerator generator = generator();
        nanoTime.addAndGet(Duration.ofMillis(71).toNanos());

        assertThatThrownBy(generator::nextId)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("租约已失效");
    }

    @Test
    void shouldFailClosedAfterGeneratorIsClosed() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList())).thenReturn(7L, 1L);
        LeaseBackedGlobalIdGenerator generator = generator();

        generator.close();

        assertThatThrownBy(generator::nextId).isInstanceOf(IdGenerationException.class);
    }

    @Test
    void shouldForbidConcurrentGenerationAfterClose() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList())).thenReturn(7L, 1L);
        LeaseBackedGlobalIdGenerator generator = generator();

        generator.close();

        List<Throwable> failures = IntStream.range(0, 8).parallel()
                .mapToObj(ignored -> catchThrowable(generator::nextId))
                .toList();

        assertThat(failures).allSatisfy(failure ->
                assertThat(failure).isInstanceOf(IdGenerationException.class));
    }

    @Test
    void shouldCancelOnlyItsOwnRenewalTaskAndKeepSharedSchedulerRunning() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList())).thenReturn(7L, 1L);
        LeaseBackedGlobalIdGenerator generator = generator();

        generator.close();

        verify(renewalTask).cancel(false);
        verify(scheduler, never()).shutdown();
        verify(scheduler, never()).shutdownNow();
    }

    @Test
    void shouldReleaseLeaseExactlyOnceOnDoubleClose() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList())).thenReturn(7L, 1L);
        LeaseBackedGlobalIdGenerator generator = generator();

        generator.close();
        generator.close();

        // 一次获取租约 + 一次释放租约
        verify(redisService, times(2)).executeLongScript(anyString(), anyList(), anyList());
        verify(renewalTask, times(1)).cancel(false);
    }

    private LeaseBackedGlobalIdGenerator generator() {
        RedisWorkerLease lease = new RedisWorkerLease(redisService, properties, nanoTime::get, "instance-a");
        return new LeaseBackedGlobalIdGenerator(lease, () -> 42L, scheduler, RENEW_INTERVAL_MILLIS);
    }
}
