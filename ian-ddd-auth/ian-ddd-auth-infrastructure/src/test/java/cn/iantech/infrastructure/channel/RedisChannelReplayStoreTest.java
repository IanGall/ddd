package cn.iantech.infrastructure.channel;

import cn.iantech.redis.IRedisService;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisChannelReplayStoreTest {

    @Test
    void shouldAtomicallyRegisterReplayKeyWithTtl() {
        IRedisService redisService = mock(IRedisService.class);
        RedisChannelReplayStore store = new RedisChannelReplayStore(redisService);
        Duration ttl = Duration.ofMinutes(5);
        when(redisService.setIfAbsent("channel:hmac:replay:request-1", "1", ttl)).thenReturn(true, false);

        assertTrue(store.markIfAbsent("request-1", ttl));
        assertFalse(store.markIfAbsent("request-1", ttl));
    }
}
