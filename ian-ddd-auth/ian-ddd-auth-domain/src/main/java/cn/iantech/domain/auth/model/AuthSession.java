package cn.iantech.domain.auth.model;

import java.io.Serializable;
import java.time.Instant;

/**
 * Auth 服务持有的服务端会话，令牌仅以 SHA-256 摘要保存。
 */
public record AuthSession(
        String sessionId,
        String familyId,
        Long accountId,
        Long userId,
        String username,
        String userType,
        String accessTokenHash,
        String refreshTokenHash,
        String clientType,
        String deviceId,
        String ipAddress,
        String userAgent,
        Instant createdAt,
        Instant accessExpiresAt,
        Instant refreshExpiresAt,
        Instant revokedAt,
        String replacedBy
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public boolean accessActive(Instant now) {
        return revokedAt == null && accessExpiresAt.isAfter(now);
    }

    public boolean refreshActive(Instant now) {
        return revokedAt == null && replacedBy == null && refreshExpiresAt.isAfter(now);
    }

    public AuthSession revoke(Instant now, String replacementSessionId) {
        return new AuthSession(sessionId, familyId, accountId, userId, username, userType,
                accessTokenHash, refreshTokenHash, clientType, deviceId, ipAddress, userAgent,
                createdAt, accessExpiresAt, refreshExpiresAt, now, replacementSessionId);
    }
}
