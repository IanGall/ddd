package cn.iantech.cases.auth.service;

import cn.iantech.common.exception.AppException;
import cn.iantech.domain.auth.infra.IAuthRiskStore;
import cn.iantech.domain.auth.infra.IAuthSessionStore;
import cn.iantech.domain.auth.model.AuthSession;
import cn.iantech.domain.auth.model.AuthTokenReference;
import cn.iantech.domain.auth.model.AuthenticatedIdentity;
import cn.iantech.domain.auth.service.IAdminIdentityAuthenticator;
import cn.iantech.domain.auth.service.ICustomerIdentityAuthenticator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static cn.iantech.cases.auth.model.AuthCaseModels.*;
import static cn.iantech.common.constant.Constants.ResponseCode.AUTH_RATE_LIMITED;
import static org.junit.jupiter.api.Assertions.*;

class AuthCaseServiceTest {

    @Test
    void shouldIssueValidateRefreshAndRevokeOpaqueSession() {
        FakeSessionStore store = new FakeSessionStore();
        AuthCaseService service = service(store, new FakeRiskStore());

        TokenResult issued = service.login(adminLogin("password"));
        assertTrue(issued.accessToken().matches("v4\\.1001\\.[A-Za-z0-9_-]{43}"));
        assertTrue(issued.refreshToken().matches("v4\\.1001\\.[A-Za-z0-9_-]{43}"));
        assertEquals("9002", issued.sessionId());
        assertEquals("9001", store.sessions.get(issued.sessionId()).familyId());
        assertTrue(store.containsAccessHash(sha256(issued.accessToken())));
        assertTrue(store.containsRefreshHash(sha256(issued.refreshToken())));
        assertNotEquals(issued.accessToken(), issued.refreshToken());
        assertEquals(1001L, service.validate(issued.accessToken()).accountId());

        TokenResult refreshed = service.refresh(new RefreshCommand(issued.refreshToken(), SUBJECT_ADMIN,
                "127.0.0.2", null));
        assertEquals(issued.identity().subjectType(), refreshed.identity().subjectType());
        assertEquals("9003", refreshed.sessionId());
        assertEquals(store.sessions.get(issued.sessionId()).familyId(),
                store.sessions.get(refreshed.sessionId()).familyId());
        assertThrows(AppException.class, () -> service.refresh(new RefreshCommand(issued.refreshToken(),
                SUBJECT_ADMIN, null, null)));
        assertThrows(AppException.class, () -> service.validate(refreshed.accessToken()));
    }

    @Test
    void shouldKeepCustomerAccountIdEmptyAndEnforceExpectedSubject() {
        FakeSessionStore store = new FakeSessionStore();
        AuthCaseService service = service(store, new FakeRiskStore());
        TokenResult issued = service.customerLogin(new LoginCommand("customer", "password", null, null,
                "127.0.0.1", null));

        assertNull(issued.identity().accountId());
        assertEquals(SUBJECT_CUSTOMER, issued.identity().subjectType());
        assertTrue(issued.accessToken().matches("v4\\.3001\\.[A-Za-z0-9_-]{43}"));
        assertThrows(AppException.class, () -> service.refresh(new RefreshCommand(issued.refreshToken(),
                SUBJECT_ADMIN, null, null)));
        assertEquals(0, store.rotationAttempts.get());
        assertEquals(SUBJECT_CUSTOMER, service.refresh(new RefreshCommand(issued.refreshToken(), SUBJECT_CUSTOMER,
                null, null)).identity().subjectType());
    }

    @Test
    void shouldRejectLegacyMalformedAndTamperedToken() {
        FakeSessionStore store = new FakeSessionStore();
        AuthCaseService service = service(store, new FakeRiskStore());
        TokenResult issued = service.login(adminLogin("password"));

        assertThrows(AppException.class, () -> service.validate("legacy-token"));
        assertThrows(AppException.class, () -> service.validate("v3.PRIMARY.1001.abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ"));
        assertThrows(AppException.class, () -> service.validate("v4.0.abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ"));
        assertThrows(AppException.class, () -> service.validate("v4.01001.abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ"));
        assertThrows(AppException.class, () -> service.validate("v4.1001.abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOP!"));
        store.ignoreTokenRouting = true;
        assertThrows(AppException.class, () -> service.validate(issued.accessToken().replace(".1001.", ".1002.")));
    }

    @Test
    void shouldRejectRevokingAnotherUsersSession() {
        FakeSessionStore store = new FakeSessionStore();
        AuthCaseService service = service(store, new FakeRiskStore());
        TokenResult admin = service.login(adminLogin("password"));
        TokenResult customer = service.customerLogin(new LoginCommand("customer", "password", null, null,
                "127.0.0.1", null));

        assertThrows(AppException.class, () -> service.revokeSession(admin.accessToken(), customer.sessionId()));
        assertEquals(SUBJECT_CUSTOMER, service.validate(customer.accessToken()).subjectType());
    }

    @Test
    void shouldAllowOnlyOneConcurrentRefresh() throws Exception {
        FakeSessionStore store = new FakeSessionStore();
        AuthCaseService service = service(store, new FakeRiskStore());
        TokenResult issued = service.login(adminLogin("password"));
        store.enableConcurrentRefreshBarrier();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = List.of(
                    executor.submit(() -> refreshSucceeded(service, issued.refreshToken())),
                    executor.submit(() -> refreshSucceeded(service, issued.refreshToken())));
            long successes = tasks.stream().map(future -> {
                try {
                    return future.get(5, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).filter(Boolean::booleanValue).count();
            assertEquals(1, successes);
        }
    }

    @Test
    void shouldApplyLoginFailureAndIpRateLimits() {
        FakeRiskStore riskStore = new FakeRiskStore();
        AuthCaseService service = service(new FakeSessionStore(), riskStore);
        for (int index = 0; index < 4; index++) {
            assertThrows(AppException.class, () -> service.login(adminLogin("wrong")));
        }
        service.login(adminLogin("password"));
        assertEquals(0, riskStore.failures.get());
        for (int index = 0; index < 5; index++) {
            assertThrows(AppException.class, () -> service.login(adminLogin("wrong")));
        }
        AppException blocked = assertThrows(AppException.class, () -> service.login(adminLogin("password")));
        assertEquals(AUTH_RATE_LIMITED.getCode(), blocked.getCode());
    }

    @Test
    void shouldKeepAtMostTenActiveDeviceFamiliesAndSupportLogoutAll() {
        FakeSessionStore store = new FakeSessionStore();
        AuthCaseService service = service(store, new FakeRiskStore());
        String firstAccessToken = service.login(adminLogin("password")).accessToken();
        for (int index = 0; index < 10; index++) {
            service.login(adminLogin("password"));
        }
        assertEquals(10, store.findActiveByUser(1001L).stream()
                .map(AuthSession::familyId).distinct().count());
        assertThrows(AppException.class, () -> service.validate(firstAccessToken));

        String current = service.login(new LoginCommand("root@1001.com", "password", "web", "device-current",
                "127.0.0.1", null)).accessToken();
        assertFalse(service.sessions(current).isEmpty());
        service.logoutAll(current);
        assertThrows(AppException.class, () -> service.validate(current));
    }

    private AuthCaseService service(FakeSessionStore store, FakeRiskStore riskStore) {
        AtomicLong ids = new AtomicLong(9000L);
        return new AuthCaseService(new FakeAdminAuthenticator(), new FakeCustomerAuthenticator(), store, riskStore,
                ids::incrementAndGet, new TokenPolicy(900, 2_592_000));
    }

    private LoginCommand adminLogin(String password) {
        return new LoginCommand("root@1001.com", password, null, null, "127.0.0.1", null);
    }

    private boolean refreshSucceeded(AuthCaseService service, String refreshToken) {
        try {
            service.refresh(new RefreshCommand(refreshToken, SUBJECT_ADMIN, null, null));
            return true;
        } catch (AppException exception) {
            return false;
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class FakeAdminAuthenticator implements IAdminIdentityAuthenticator {
        @Override
        public AuthenticatedIdentity authenticate(String loginName, String password) {
            return "root@1001.com".equals(loginName) && "password".equals(password) ? result() : null;
        }

        @Override
        public AuthenticatedIdentity reload(Long accountId, Long userId, String userType) {
            return Long.valueOf(1001L).equals(accountId) && Long.valueOf(1001L).equals(userId)
                    && "PRIMARY".equals(userType) ? result() : null;
        }

        private AuthenticatedIdentity result() {
            return new AuthenticatedIdentity(1001L, 1001L, "root", "PRIMARY", List.of(), List.of());
        }
    }

    private static final class FakeCustomerAuthenticator implements ICustomerIdentityAuthenticator {
        @Override
        public AuthenticatedIdentity authenticate(String loginName, String password) {
            return "customer".equals(loginName) && "password".equals(password) ? result() : null;
        }

        @Override
        public AuthenticatedIdentity reload(Long customerId) {
            return Long.valueOf(3001L).equals(customerId) ? result() : null;
        }

        private AuthenticatedIdentity result() {
            return new AuthenticatedIdentity(3001L, null, "customer", "CUSTOMER", List.of(), List.of());
        }
    }

    private static final class FakeRiskStore implements IAuthRiskStore {
        private final AtomicInteger ipAttempts = new AtomicInteger();
        private final AtomicInteger failures = new AtomicInteger();

        @Override
        public boolean allowIpAttempt(String ipAddressHash, int maximumAttempts, Duration window) {
            return ipAttempts.incrementAndGet() <= maximumAttempts;
        }

        @Override
        public boolean isLoginBlocked(String loginNameHash, int maximumFailures) {
            return failures.get() >= maximumFailures;
        }

        @Override
        public boolean recordLoginFailure(String loginNameHash, int maximumFailures, Duration lockDuration) {
            return failures.incrementAndGet() >= maximumFailures;
        }

        @Override
        public void clearLoginFailures(String loginNameHash) {
            failures.set(0);
        }
    }

    private static final class FakeSessionStore implements IAuthSessionStore {
        private final Map<String, AuthSession> sessions = new ConcurrentHashMap<>();
        private final Map<String, String> accessIndex = new ConcurrentHashMap<>();
        private final Map<String, String> refreshIndex = new ConcurrentHashMap<>();
        private final AtomicInteger rotationAttempts = new AtomicInteger();
        private volatile CyclicBarrier refreshBarrier;
        private volatile boolean ignoreTokenRouting;

        @Override
        public synchronized void create(AuthSession session, int maximumActiveFamilies) {
            store(session);
            List<String> activeFamilies = sessions.values().stream()
                    .filter(value -> value.userId().equals(session.userId()))
                    .filter(value -> value.refreshActive(Instant.now()))
                    .sorted((left, right) -> left.createdAt().compareTo(right.createdAt()))
                    .map(AuthSession::familyId).distinct().toList();
            activeFamilies.stream().limit(Math.max(0, activeFamilies.size() - maximumActiveFamilies))
                    .forEach(familyId -> revokeFamily(session.userId(), familyId, Instant.now()));
        }

        @Override
        public synchronized RotationResult rotate(AuthTokenReference refreshToken, AuthSession replacement,
                                                  Instant rotatedAt) {
            rotationAttempts.incrementAndGet();
            AuthSession current = find(refreshToken.userId(), refreshIndex.get(refreshToken.tokenHash())).orElse(null);
            if (current == null || current.refreshExpiresAt().compareTo(rotatedAt) <= 0) {
                return RotationResult.INVALID;
            }
            if (current.revokedAt() != null || current.replacedBy() != null) {
                revokeFamily(current.userId(), current.familyId(), rotatedAt);
                return RotationResult.REPLAY_REVOKED;
            }
            sessions.put(current.sessionId(), current.revoke(rotatedAt, replacement.sessionId()));
            accessIndex.remove(current.accessTokenHash());
            store(replacement);
            return RotationResult.ROTATED;
        }

        @Override
        public synchronized void revokeSession(Long userId, String sessionId, Instant revokedAt) {
            find(userId, sessionId).ifPresent(session -> revokeFamily(userId, session.familyId(), revokedAt));
        }

        @Override
        public synchronized void revokeFamily(Long userId, String familyId, Instant revokedAt) {
            sessions.values().stream().filter(session -> session.userId().equals(userId))
                    .filter(session -> session.familyId().equals(familyId))
                    .filter(session -> session.revokedAt() == null)
                    .forEach(session -> {
                        sessions.put(session.sessionId(), session.revoke(revokedAt, session.replacedBy()));
                        accessIndex.remove(session.accessTokenHash());
                    });
        }

        @Override
        public synchronized void revokeFamilies(Long userId, Collection<String> familyIds, Instant revokedAt) {
            familyIds.stream().filter(familyId -> familyId != null).distinct()
                    .forEach(familyId -> revokeFamily(userId, familyId, revokedAt));
        }

        @Override
        public Optional<AuthSession> findByAccessToken(AuthTokenReference token) {
            if (ignoreTokenRouting) {
                return sessions.values().stream().findFirst();
            }
            return find(token.userId(), accessIndex.get(token.tokenHash()));
        }

        @Override
        public Optional<AuthSession> findByRefreshToken(AuthTokenReference token) {
            awaitRefreshBarrier();
            return find(token.userId(), refreshIndex.get(token.tokenHash()));
        }

        @Override
        public Optional<AuthSession> findBySessionId(Long userId, String sessionId) {
            return find(userId, sessionId);
        }

        @Override
        public List<AuthSession> findActiveByUser(Long userId) {
            Instant now = Instant.now();
            return sessions.values().stream().filter(session -> session.userId().equals(userId))
                    .filter(session -> session.refreshActive(now))
                    .toList();
        }

        private void store(AuthSession session) {
            sessions.put(session.sessionId(), session);
            accessIndex.put(session.accessTokenHash(), session.sessionId());
            refreshIndex.put(session.refreshTokenHash(), session.sessionId());
        }

        private boolean containsAccessHash(String hash) {
            return accessIndex.containsKey(hash);
        }

        private boolean containsRefreshHash(String hash) {
            return refreshIndex.containsKey(hash);
        }

        private Optional<AuthSession> find(Long userId, String sessionId) {
            return Optional.ofNullable(sessionId).map(sessions::get).filter(session -> session.userId().equals(userId));
        }

        private void enableConcurrentRefreshBarrier() {
            refreshBarrier = new CyclicBarrier(2);
        }

        private void awaitRefreshBarrier() {
            CyclicBarrier barrier = refreshBarrier;
            if (barrier != null) {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                } finally {
                    refreshBarrier = null;
                }
            }
        }
    }
}
