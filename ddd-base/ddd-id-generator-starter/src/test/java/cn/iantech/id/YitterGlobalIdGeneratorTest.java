package cn.iantech.id;

import cn.iantech.redis.IRedisService;
import com.github.yitter.contract.IIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class YitterGlobalIdGeneratorTest {

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
    void shouldDelegateGenerationWhileLeaseIsValid() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList())).thenReturn(7L, 1L);
        RedisWorkerLease lease = new RedisWorkerLease(redisService, properties, nanoTime::get, "instance-a");
        IIdGenerator delegate = () -> 42L;
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        YitterGlobalIdGenerator generator = new YitterGlobalIdGenerator(lease, delegate, executor, 30L);

        assertThat(generator.nextId()).isEqualTo(42L);

        generator.close();
        verify(executor).shutdownNow();
    }

    @Test
    void shouldFailClosedAfterLeaseExpires() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList())).thenReturn(7L);
        RedisWorkerLease lease = new RedisWorkerLease(redisService, properties, nanoTime::get, "instance-a");
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        YitterGlobalIdGenerator generator = new YitterGlobalIdGenerator(lease, () -> 42L, executor, 30L);
        nanoTime.addAndGet(Duration.ofMillis(71).toNanos());

        assertThatThrownBy(generator::nextId)
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("租约已失效");
    }

    @Test
    void shouldFailClosedAfterGeneratorIsClosed() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList())).thenReturn(7L, 1L);
        RedisWorkerLease lease = new RedisWorkerLease(redisService, properties, nanoTime::get, "instance-a");
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        YitterGlobalIdGenerator generator = new YitterGlobalIdGenerator(lease, () -> 42L, executor, 30L);

        generator.close();

        assertThatThrownBy(generator::nextId).isInstanceOf(IdGenerationException.class);
    }
}
