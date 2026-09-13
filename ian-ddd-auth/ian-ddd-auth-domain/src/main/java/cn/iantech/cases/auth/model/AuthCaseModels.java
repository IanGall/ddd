package cn.iantech.cases.auth.model;

import java.time.Instant;
import java.util.List;

/**
 * 认证用例的输入输出模型，与 RPC/API 传输模型解耦。
 */
public final class AuthCaseModels {

    public static final String SUBJECT_ADMIN = "ADMIN";
    public static final String SUBJECT_ADMIN_PRIMARY = "ADMIN_PRIMARY";
    public static final String SUBJECT_ADMIN_SUB_ACCOUNT = "ADMIN_SUB_ACCOUNT";
    public static final String SUBJECT_CUSTOMER = "CUSTOMER";

    private AuthCaseModels() {
    }

    public record LoginCommand(String loginName, String password, String clientType, String deviceId,
                               String ipAddress, String userAgent) {
    }

    public record RefreshCommand(String refreshToken, String expectedSubjectType, String ipAddress,
                                 String userAgent) {
    }

    public record IdentityResult(Long accountId, Long userId, String username, String userType,
                                 String subjectType, String subjectId, String clientId, List<String> scopes,
                                 String issuer, String tokenKind, String sessionId, Long ownerAccountId,
                                 Long credentialVersion, String authorizedScope) {
    }

    public record TokenResult(String accessToken, String refreshToken, String tokenType, long expiresIn,
                              long refreshExpiresIn, String sessionId, IdentityResult identity) {
    }

    public record SessionResult(String sessionId, String clientType, String deviceId, String ipAddress,
                                String userAgent, Instant createdAt, Instant expiresAt, boolean current) {
    }

    public record TokenPolicy(long accessTokenTimeout, long refreshTokenTimeout) {

        public TokenPolicy {
            if (accessTokenTimeout <= 0 || refreshTokenTimeout <= accessTokenTimeout) {
                throw new IllegalArgumentException("刷新令牌有效期必须大于访问令牌有效期");
            }
        }
    }
}
