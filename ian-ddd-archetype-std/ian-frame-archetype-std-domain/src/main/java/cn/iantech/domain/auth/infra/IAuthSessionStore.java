package cn.iantech.domain.auth.infra;

import cn.iantech.domain.auth.model.AuthSession;
import cn.iantech.domain.auth.model.AuthTokenReference;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Auth 会话持久化端口，由 Infrastructure 负责 Redis 实现。
 */
public interface IAuthSessionStore {

    /**
     * 原子创建新设备会话，并在超过上限时撤销最早创建的设备会话族。
     */
    void create(AuthSession session, int maximumActiveFamilies);

    /**
     * 按 Refresh Token 摘要执行原子轮换。
     */
    RotationResult rotate(AuthTokenReference refreshToken, AuthSession replacement, Instant rotatedAt);

    /**
     * 撤销指定会话所属的整个设备会话族。
     */
    void revokeSession(Long userId, String sessionId, Instant revokedAt);

    /**
     * 撤销整个设备会话族。
     */
    void revokeFamily(Long userId, String familyId, Instant revokedAt);

    Optional<AuthSession> findByAccessToken(AuthTokenReference token);

    Optional<AuthSession> findByRefreshToken(AuthTokenReference token);

    Optional<AuthSession> findBySessionId(Long userId, String sessionId);

    List<AuthSession> findActiveByUser(Long userId);

    enum RotationResult {
        ROTATED,
        INVALID,
        REPLAY_REVOKED
    }
}
