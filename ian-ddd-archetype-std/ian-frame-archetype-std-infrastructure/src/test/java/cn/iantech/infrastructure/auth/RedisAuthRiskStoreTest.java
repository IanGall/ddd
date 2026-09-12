package cn.iantech.infrastructure.auth;

import cn.iantech.redis.IRedisService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RedisAuthRiskStoreTest {

    private final IRedisService redisService = mock(IRedisService.class);
    private final RedisAuthRiskStore store = new RedisAuthRiskStore(redisService);

    @Test
    void shouldUseAtomicScriptsAndClearLoginState() {
        when(redisService.executeLongScript(anyString(), anyList(), anyList())).thenReturn(1L, 0L);
        when(redisService.getString(anyString())).thenReturn("5");

        assertTrue(store.allowIpAttempt("ip-hash", 10, Duration.ofMinutes(1)));
        assertFalse(store.recordLoginFailure("login-hash", 5, Duration.ofMinutes(10)));
        assertTrue(store.isLoginBlocked("login-hash", 5));
        store.clearLoginFailures("login-hash");

        verify(redisService).executeLongScript(anyString(), eq(List.of("auth:risk:v1:ip:ip-hash")), anyList());
        verify(redisService).executeLongScript(anyString(), eq(List.of("auth:risk:v1:login:login-hash")), anyList());
        verify(redisService).getString("auth:risk:v1:login:login-hash");
        verify(redisService).remove("auth:risk:v1:login:login-hash");
        assertFalse(AuthRedisKey.riskLogin("login-hash").contains("{"));
    }

    @Test
    void shouldRejectAmbiguousRiskKeySegments() {
        assertThrows(IllegalArgumentException.class,
                () -> store.allowIpAttempt("invalid{hash}", 10, Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> store.clearLoginFailures("invalid:hash"));
        verifyNoInteractions(redisService);
    }
}
