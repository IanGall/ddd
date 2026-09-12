package cn.iantech.infrastructure.auth;

import cn.iantech.domain.auth.infra.IAuthSessionStore.RotationResult;
import cn.iantech.domain.auth.model.AuthSession;
import cn.iantech.domain.auth.model.AuthTokenReference;
import cn.iantech.redis.IRedisService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisAuthSessionStoreTest {

    private static final String ACCESS_HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String REFRESH_HASH = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String OLD_REFRESH_HASH = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";

    private final IRedisService redisService = mock(IRedisService.class);
    private final RedisAuthSessionStore store = new RedisAuthSessionStore(redisService);

    @Test
    void shouldCreateFiveKeysInSameScopeSlotAndKeepFullTokenDigests() {
        AuthSession session = session("session-2", "family-1", 1L, 2L, "SUB_ACCOUNT", Instant.now());
        when(redisService.executeLongScript(anyString(), anyList(), anyList())).thenReturn(1L);

        store.create(session, 3);

        ArgumentCaptor<List<String>> keysCaptor = stringListCaptor();
        verify(redisService).executeLongScript(anyString(), keysCaptor.capture(), anyList());
        List<String> keys = keysCaptor.getValue();
        String prefix = "auth:session:v4:{2}:";
        assertEquals(List.of(prefix + "session:session-2", prefix + "access:" + ACCESS_HASH,
                prefix + "refresh:" + REFRESH_HASH, prefix + "family:family-1", prefix + "user"), keys);
        assertEquals(5, keys.size());
        assertSameHashTag(keys);
    }

    @Test
    void shouldRotateSixKeysInSameScopeSlotAndMapResults() {
        AuthSession session = session("session-2", "family-1", 1L, 2L, "SUB_ACCOUNT", Instant.now());
        AuthTokenReference refreshToken = new AuthTokenReference(session.userId(), OLD_REFRESH_HASH);
        when(redisService.executeLongScript(anyString(), anyList(), anyList()))
                .thenReturn(0L, 1L, 2L);

        assertEquals(RotationResult.INVALID, store.rotate(refreshToken, session, Instant.now()));
        assertEquals(RotationResult.ROTATED, store.rotate(refreshToken, session, Instant.now()));
        assertEquals(RotationResult.REPLAY_REVOKED, store.rotate(refreshToken, session, Instant.now()));

        ArgumentCaptor<List<String>> keysCaptor = stringListCaptor();
        verify(redisService, times(3)).executeLongScript(anyString(), keysCaptor.capture(), anyList());
        keysCaptor.getAllValues().forEach(keys -> {
            assertEquals(6, keys.size());
            assertTrue(keys.getFirst().endsWith("refresh:" + OLD_REFRESH_HASH));
            assertSameHashTag(keys);
        });
    }

    @Test
    void shouldSeparateUserTagsAndSupportCustomerWithoutAccount() {
        AuthSession admin = session("admin-session", "admin-family", 1L, 2L, "SUB_ACCOUNT", Instant.now());
        AuthSession customer = session("customer-session", "customer-family", null, 3L, "CUSTOMER", Instant.now());
        when(redisService.executeLongScript(anyString(), anyList(), anyList())).thenReturn(1L);

        store.create(admin, 3);
        store.create(customer, 3);

        ArgumentCaptor<List<String>> keysCaptor = stringListCaptor();
        verify(redisService, times(2)).executeLongScript(anyString(), keysCaptor.capture(), anyList());
        List<String> adminKeys = keysCaptor.getAllValues().get(0);
        List<String> customerKeys = keysCaptor.getAllValues().get(1);
        assertSameHashTag(adminKeys);
        assertSameHashTag(customerKeys);
        assertNotEquals(hashTag(adminKeys.getFirst()), hashTag(customerKeys.getFirst()));
        assertEquals("{3}", hashTag(customerKeys.getFirst()));
    }

    @Test
    void shouldRejectAmbiguousKeySegmentsAndMismatchedRotationScope() {
        AuthSession session = session("session-2", "family-1", 1L, 2L, "SUB_ACCOUNT", Instant.now());

        assertThrows(IllegalArgumentException.class,
                () -> store.findBySessionId(session.userId(), "invalid:session"));
        assertThrows(IllegalArgumentException.class,
                () -> store.rotate(new AuthTokenReference(3L, OLD_REFRESH_HASH),
                        session, Instant.now()));
        verifyNoInteractions(redisService);
    }

    @Test
    void shouldBatchLoadActiveSessions() {
        Instant now = Instant.now();
        AuthSession active = session("session-active", "family-1", 1L, 2L, "SUB_ACCOUNT", now);
        AuthSession expired = session("session-expired", "family-1", 1L, 2L, "SUB_ACCOUNT",
                now.minusSeconds(7200));
        when(redisService.rangeSortedSetByScore(anyString(), anyDouble(), eq(false),
                eq(Double.POSITIVE_INFINITY), eq(true)))
                .thenReturn(List.of(active.sessionId(), expired.sessionId()));
        when(redisService.readStringHashes(anyList())).thenReturn(Map.of(
                AuthRedisKey.session(active.userId(), active.sessionId()), values(active),
                AuthRedisKey.session(expired.userId(), expired.sessionId()), values(expired)));

        List<AuthSession> sessions = store.findActiveByUser(active.userId());

        assertEquals(1, sessions.size());
        assertEquals(active.sessionId(), sessions.getFirst().sessionId());
        assertEquals(active.refreshTokenHash(), sessions.getFirst().refreshTokenHash());
        verify(redisService).readStringHashes(List.of(
                AuthRedisKey.session(active.userId(), active.sessionId()),
                AuthRedisKey.session(expired.userId(), expired.sessionId())));
    }

    private AuthSession session(String sessionId, String familyId, Long accountId, Long userId, String userType,
                                Instant baseTime) {
        return new AuthSession(sessionId, familyId, accountId, userId, "operator", userType,
                ACCESS_HASH, REFRESH_HASH, "web", "device-1", "ip", "agent",
                baseTime, baseTime.plusSeconds(900), baseTime.plusSeconds(3600), null, null);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<String>> stringListCaptor() {
        return ArgumentCaptor.forClass((Class<List<String>>) (Class<?>) List.class);
    }

    private void assertSameHashTag(List<String> keys) {
        assertEquals(1, keys.stream().map(this::hashTag).distinct().count());
    }

    private String hashTag(String key) {
        return key.substring(key.indexOf('{'), key.indexOf('}') + 1);
    }

    private Map<String, String> values(AuthSession session) {
        return Map.ofEntries(
                Map.entry("sessionId", session.sessionId()), Map.entry("familyId", session.familyId()),
                Map.entry("accountId", session.accountId().toString()),
                Map.entry("userId", session.userId().toString()), Map.entry("username", session.username()),
                Map.entry("userType", session.userType()), Map.entry("accessTokenHash", session.accessTokenHash()),
                Map.entry("refreshTokenHash", session.refreshTokenHash()), Map.entry("clientType", session.clientType()),
                Map.entry("deviceId", session.deviceId()), Map.entry("ipAddress", session.ipAddress()),
                Map.entry("userAgent", session.userAgent()),
                Map.entry("createdAt", Long.toString(session.createdAt().toEpochMilli())),
                Map.entry("accessExpiresAt", Long.toString(session.accessExpiresAt().toEpochMilli())),
                Map.entry("refreshExpiresAt", Long.toString(session.refreshExpiresAt().toEpochMilli())),
                Map.entry("revokedAt", ""), Map.entry("replacedBy", ""));
    }
}
