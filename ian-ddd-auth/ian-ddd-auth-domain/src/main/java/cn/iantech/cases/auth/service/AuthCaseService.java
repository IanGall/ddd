package cn.iantech.cases.auth.service;

import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.common.util.Sha256;
import cn.iantech.domain.auth.infra.IAuthIdGenerator;
import cn.iantech.domain.auth.infra.IAuthRiskStore;
import cn.iantech.domain.auth.infra.IAuthSessionStore;
import cn.iantech.domain.auth.model.AuthSession;
import cn.iantech.domain.auth.model.AuthTokenFormat;
import cn.iantech.domain.auth.model.AuthTokenReference;
import cn.iantech.domain.auth.model.AuthUserTypes;
import cn.iantech.domain.auth.model.AuthenticatedIdentity;
import cn.iantech.domain.auth.service.IAdminIdentityAuthenticator;
import cn.iantech.domain.auth.service.ICustomerIdentityAuthenticator;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static cn.iantech.cases.auth.model.AuthCaseModels.*;
import static cn.iantech.common.constant.Constants.ResponseCode.*;

/**
 * 认证用例编排：统一处理登录风控、opaque Token 生命周期及会话撤销。
 */
@Service
public class AuthCaseService {
    private static final String TOKEN_TYPE = "Bearer";
    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_SECRET_LENGTH = 43;
    private static final int MAXIMUM_ACTIVE_FAMILIES = 10;
    private static final int MAXIMUM_IP_ATTEMPTS = 30;
    private static final int MAXIMUM_LOGIN_FAILURES = 5;
    private static final Duration IP_ATTEMPT_WINDOW = Duration.ofSeconds(60);
    private static final Duration LOGIN_LOCK_DURATION = Duration.ofMinutes(15);

    private final IAdminIdentityAuthenticator adminAuthenticator;
    private final ICustomerIdentityAuthenticator customerAuthenticator;
    private final IAuthSessionStore sessionStore;
    private final IAuthRiskStore riskStore;
    private final IAuthIdGenerator idGenerator;
    private final TokenPolicy tokenPolicy;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthCaseService(IAdminIdentityAuthenticator adminAuthenticator,
                           ICustomerIdentityAuthenticator customerAuthenticator,
                           IAuthSessionStore sessionStore,
                           IAuthRiskStore riskStore,
                           IAuthIdGenerator idGenerator,
                           TokenPolicy tokenPolicy) {
        this.adminAuthenticator = adminAuthenticator;
        this.customerAuthenticator = customerAuthenticator;
        this.sessionStore = sessionStore;
        this.riskStore = riskStore;
        this.idGenerator = idGenerator;
        this.tokenPolicy = tokenPolicy;
    }

    // 纯 Redis + 密码哈希路径，不触碰 DB 写，避免事务连接被慢操作长期占用
    public TokenResult login(LoginCommand command) {
        return login(command, false);
    }

    public TokenResult customerLogin(LoginCommand command) {
        return login(command, true);
    }

    public TokenResult refresh(RefreshCommand command) {
        if (command == null || command.refreshToken() == null || command.refreshToken().isBlank()) {
            throw unauthorized("刷新令牌不能为空");
        }
        AuthTokenReference refreshToken = tokenReference(command.refreshToken());
        AuthSession candidate = findByRefreshToken(refreshToken)
                .orElseThrow(() -> unauthorized("刷新令牌无效或已过期"));
        return rotate(refreshToken, candidate, command);
    }

    public IdentityResult validate(String accessToken) {
        AuthSession session = accessSession(accessToken);
        AuthenticatedIdentity identity = reload(session);
        if (identity == null || sameIdentity(session, identity)) {
            sessionStore.revokeFamily(session.userId(), session.familyId(), Instant.now());
            throw unauthorized("账号状态已失效，请重新登录");
        }
        return identity(session);
    }

    public void logout(String accessToken) {
        if (accessToken == null) {
            return;
        }
        AuthTokenReference reference = tokenReference(accessToken);
        findByAccessToken(reference).ifPresent(session ->
                sessionStore.revokeSession(reference.userId(), session.sessionId(), Instant.now()));
    }

    public void logoutAll(String accessToken) {
        AuthSession current = accessSession(accessToken);
        Long userId = current.userId();
        sessionStore.findActiveByUser(userId).stream()
                .map(AuthSession::familyId).distinct()
                .forEach(familyId -> sessionStore.revokeFamily(userId, familyId, Instant.now()));
    }

    public List<SessionResult> sessions(String accessToken) {
        AuthSession current = accessSession(accessToken);
        return sessionStore.findActiveByUser(current.userId()).stream()
                .map(session -> new SessionResult(session.sessionId(), session.clientType(), session.deviceId(),
                        session.ipAddress(), session.userAgent(), session.createdAt(), session.refreshExpiresAt(),
                        Objects.equals(session.sessionId(), current.sessionId())))
                .toList();
    }

    public void revokeSession(String accessToken, String sessionId) {
        AuthSession current = accessSession(accessToken);
        if (sessionId == null || sessionId.isBlank()) {
            throw denied("会话ID不能为空");
        }
        Long userId = current.userId();
        AuthSession target = sessionStore.findBySessionId(userId, sessionId)
                .orElseThrow(() -> denied("会话不存在或无权访问"));
        if (!Objects.equals(current.accountId(), target.accountId())
                || !Objects.equals(current.userId(), target.userId())
                || !Objects.equals(current.userType(), target.userType())) {
            throw denied("会话不存在或无权访问");
        }
        sessionStore.revokeFamily(userId, target.familyId(), Instant.now());
    }

    private TokenResult login(LoginCommand command, boolean customer) {
        if (command == null || (customer && (command.loginName() == null || command.password() == null))) {
            throw unauthorized(customer ? "账号或密码不能为空" : "登录请求不能为空");
        }
        String ipAddressHash = Sha256.hex(value(command.ipAddress(), "unknown"));
        // 登录名归一化后再作为风控计数键：管理端与 C 端必须一致，
        // 否则同一账号的大小写变体各自计数，账号级锁定会被绕过
        String loginName = value(command.loginName(), "").trim().toLowerCase(Locale.ROOT);
        String loginNameHash = Sha256.hex(loginName);
        if (!riskStore.allowIpAttempt(ipAddressHash, MAXIMUM_IP_ATTEMPTS, IP_ATTEMPT_WINDOW)
                || riskStore.isLoginBlocked(loginNameHash, MAXIMUM_LOGIN_FAILURES)) {
            throw rateLimited();
        }
        AuthenticatedIdentity result = customer
                ? customerAuthenticator.authenticate(command.loginName(), command.password())
                : adminAuthenticator.authenticate(command.loginName(), command.password());
        if (result == null) {
            if (riskStore.recordLoginFailure(loginNameHash, MAXIMUM_LOGIN_FAILURES, LOGIN_LOCK_DURATION)) {
                throw rateLimited();
            }
            throw unauthorized("账号或密码错误");
        }
        riskStore.clearLoginFailures(loginNameHash);
        IssuedSession issued = prepareIssue(result,
                metadata(command.clientType(), command.deviceId(), command.ipAddress(), command.userAgent()),
                nextId(), Instant.now());
        sessionStore.create(issued.session(), MAXIMUM_ACTIVE_FAMILIES);
        return issued.token();
    }

    private TokenResult rotate(AuthTokenReference refreshToken, AuthSession current, RefreshCommand command) {
        Instant now = Instant.now();
        if (!current.refreshActive(now)) {
            if (current.revokedAt() != null || current.replacedBy() != null) {
                sessionStore.revokeFamily(current.userId(), current.familyId(), now);
                throw unauthorized("检测到刷新令牌重放，当前设备会话已撤销");
            }
            throw unauthorized("刷新令牌无效或已过期");
        }
        validateExpectedSubjectType(command.expectedSubjectType(), current.userType());
        AuthenticatedIdentity result = reload(current);
        if (result == null || sameIdentity(current, result)) {
            sessionStore.revokeFamily(current.userId(), current.familyId(), now);
            throw unauthorized("账号状态已失效，请重新登录");
        }
        ClientMetadata metadata = metadata(current.clientType(), current.deviceId(), command.ipAddress(), command.userAgent());
        IssuedSession issued = prepareIssue(result, metadata, current.familyId(), current.createdAt());
        IAuthSessionStore.RotationResult rotation = sessionStore.rotate(refreshToken, issued.session(), now);
        if (rotation == IAuthSessionStore.RotationResult.REPLAY_REVOKED) {
            throw unauthorized("检测到刷新令牌重放，当前设备会话已撤销");
        }
        if (rotation != IAuthSessionStore.RotationResult.ROTATED) {
            throw unauthorized("刷新令牌无效或已过期");
        }
        return issued.token();
    }

    private void validateExpectedSubjectType(String expectedSubjectType, String userType) {
        boolean matched = switch (value(expectedSubjectType, "")) {
            case SUBJECT_ADMIN -> AuthUserTypes.PRIMARY.equals(userType) || AuthUserTypes.SUB_ACCOUNT.equals(userType);
            case SUBJECT_ADMIN_PRIMARY -> AuthUserTypes.PRIMARY.equals(userType);
            case SUBJECT_ADMIN_SUB_ACCOUNT -> AuthUserTypes.SUB_ACCOUNT.equals(userType);
            case SUBJECT_CUSTOMER -> AuthUserTypes.CUSTOMER.equals(userType);
            default -> false;
        };
        if (!matched) {
            throw unauthorized("刷新令牌主体类型不匹配");
        }
    }

    private AuthenticatedIdentity reload(AuthSession session) {
        return AuthUserTypes.CUSTOMER.equals(session.userType())
                ? session.accountId() == null ? customerAuthenticator.reload(session.userId()) : null
                : adminAuthenticator.reload(session.accountId(), session.userId(), session.userType());
    }

    private IssuedSession prepareIssue(AuthenticatedIdentity result, ClientMetadata metadata,
                                       String familyId, Instant createdAt) {
        if (result == null || result.userId() == null || result.username() == null || result.userType() == null
                || (!AuthUserTypes.CUSTOMER.equals(result.userType()) && result.accountId() == null)) {
            throw unauthorized("认证身份无效");
        }
        Instant issuedAt = Instant.now();
        if (result.userId() <= 0) {
            throw unauthorized("认证身份无效");
        }
        String accessToken = randomToken(result.userId());
        String refreshToken = randomToken(result.userId());
        AuthSession session = new AuthSession(nextId(), familyId, result.accountId(), result.userId(),
                result.username(), result.userType(), Sha256.hex(accessToken), Sha256.hex(refreshToken),
                metadata.clientType(),
                metadata.deviceId(), metadata.ipAddress(), metadata.userAgent(), createdAt,
                issuedAt.plusSeconds(tokenPolicy.accessTokenTimeout()),
                issuedAt.plusSeconds(tokenPolicy.refreshTokenTimeout()), null, null);
        TokenResult token = new TokenResult(accessToken, refreshToken, TOKEN_TYPE, tokenPolicy.accessTokenTimeout(),
                tokenPolicy.refreshTokenTimeout(), session.sessionId(), identity(session));
        return new IssuedSession(session, token);
    }

    private AuthSession accessSession(String token) {
        if (token == null || token.isBlank()) {
            throw unauthorized("需要认证");
        }
        AuthTokenReference reference = tokenReference(token);
        AuthSession session = findByAccessToken(reference)
                .orElseThrow(() -> unauthorized("令牌无效或已过期"));
        if (!session.accessActive(Instant.now())) {
            throw unauthorized("令牌无效或已过期");
        }
        return session;
    }

    private boolean sameIdentity(AuthSession session, AuthenticatedIdentity result) {
        return !Objects.equals(session.accountId(), result.accountId())
                || !Objects.equals(session.userId(), result.userId())
                || !Objects.equals(session.userType(), result.userType());
    }

    private IdentityResult identity(AuthSession session) {
        String subjectType = switch (session.userType()) {
            case AuthUserTypes.PRIMARY -> SUBJECT_ADMIN_PRIMARY;
            case AuthUserTypes.SUB_ACCOUNT -> SUBJECT_ADMIN_SUB_ACCOUNT;
            case AuthUserTypes.CUSTOMER -> SUBJECT_CUSTOMER;
            default -> session.userType();
        };
        return new IdentityResult(session.accountId(), session.userId(), session.username(), session.userType(),
                subjectType, String.valueOf(session.userId()), null, List.of(), "ian-auth", Constants.TokenKind.OPAQUE,
                session.sessionId(), null, null, null);
    }

    private ClientMetadata metadata(String clientType, String deviceId, String ipAddress, String userAgent) {
        return new ClientMetadata(bound(value(clientType, "unknown"), 32),
                bound(value(deviceId, UUID.randomUUID().toString()), 128), bound(value(ipAddress, "unknown"), 64),
                bound(value(userAgent, "unknown"), 512));
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String bound(String value, int maximumLength) {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    private String randomToken(Long userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return AuthTokenFormat.VERSION + "." + userId + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private AuthTokenReference tokenReference(String token) {
        if (token == null || token.isBlank()) {
            throw unauthorized("令牌无效或已过期");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !AuthTokenFormat.VERSION.equals(parts[0])
                || parts[2].length() != TOKEN_SECRET_LENGTH) {
            throw unauthorized("令牌无效或已过期");
        }
        try {
            long userId = Long.parseLong(parts[1]);
            byte[] secret = Base64.getUrlDecoder().decode(parts[2]);
            if (userId <= 0 || !Long.toString(userId).equals(parts[1]) || secret.length != TOKEN_BYTES
                    || !Base64.getUrlEncoder().withoutPadding().encodeToString(secret).equals(parts[2])) {
                throw new IllegalArgumentException("Token 格式不规范");
            }
            return new AuthTokenReference(userId, Sha256.hex(token));
        } catch (IllegalArgumentException exception) {
            throw unauthorized("令牌无效或已过期");
        }
    }

    private Optional<AuthSession> findByAccessToken(AuthTokenReference reference) {
        return verifiedScope(reference, sessionStore.findByAccessToken(reference));
    }

    private Optional<AuthSession> findByRefreshToken(AuthTokenReference reference) {
        return verifiedScope(reference, sessionStore.findByRefreshToken(reference));
    }

    private Optional<AuthSession> verifiedScope(AuthTokenReference reference, Optional<AuthSession> session) {
        if (session.isPresent() && !reference.userId().equals(session.get().userId())) {
            throw unauthorized("令牌无效或已过期");
        }
        return session;
    }

    private String nextId() {
        long id = idGenerator.nextId();
        if (id <= 0) {
            throw new IllegalStateException("Auth ID 生成器返回了非正数");
        }
        return Long.toString(id);
    }

    private AppException unauthorized(String message) {
        return new AppException(AUTH_REQUIRED.getCode(), message);
    }

    private AppException denied(String message) {
        return new AppException(ACCESS_DENIED.getCode(), message);
    }

    private AppException rateLimited() {
        return new AppException(AUTH_RATE_LIMITED.getCode(), AUTH_RATE_LIMITED.getInfo());
    }

    private record ClientMetadata(String clientType, String deviceId, String ipAddress, String userAgent) {
    }

    private record IssuedSession(AuthSession session, TokenResult token) {
    }
}
