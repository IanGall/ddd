package cn.iantech.id.core;

import cn.iantech.id.IdGenerationException;
import cn.iantech.id.autoconfigure.IdGeneratorProperties;
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
        properties.setNamespace("ddd-global-id");
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
    void shouldServeMultipleBusinessGeneratorsFromOneAcquiredLease() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList())).thenReturn(7L, 1L);
        WorkerIdLeaseHolder holder = holder();
        LeaseBackedGlobalIdGenerator first = new LeaseBackedGlobalIdGenerator(holder, () -> 1L);
        LeaseBackedGlobalIdGenerator second = new LeaseBackedGlobalIdGenerator(holder, () -> 2L);

        assertThat(first.nextId()).isEqualTo(1L);
        assertThat(second.nextId()).isEqualTo(2L);

        // 全部业务共用同一个实例级 Worker ID，因此只应取租约一次
        verify(redisService, times(1)).executeLongScript(anyString(), anyList(), anyList());

        // 共享租约下的关闭是整体生效的：租约是实例级的，一个业务关闭即释放实例租约
        first.close();
        assertThatThrownBy(second::nextId).isInstanceOf(IdGenerationException.class);
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

    private WorkerIdLeaseHolder holder() {
        RedisWorkerLease lease = new RedisWorkerLease(redisService, properties, nanoTime::get, "instance-a");
        return new WorkerIdLeaseHolder(lease, scheduler, RENEW_INTERVAL_MILLIS);
    }

    private LeaseBackedGlobalIdGenerator generator() {
        return new LeaseBackedGlobalIdGenerator(holder(), () -> 42L);
    }
}
