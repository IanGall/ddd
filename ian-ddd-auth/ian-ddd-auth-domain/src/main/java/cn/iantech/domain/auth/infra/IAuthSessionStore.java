package cn.iantech.domain.auth.infra;

import cn.iantech.domain.auth.model.AuthSession;
import cn.iantech.domain.auth.model.AuthTokenReference;

import java.time.Instant;
import java.util.Collection;
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

    /**
     * 批量撤销多个设备会话族：语义与逐个调用 {@link #revokeFamily} 等价，但只产生一次往返。
     *
     * <p><b>空集合或无有效项时为无操作</b>，不产生任何存储调用。</p>
     *
     * <p><b>并发语义</b>：只撤销调用方传入的 familyId 集合。调用方通常先查询活动会话再传入，
     * 该查询与本次撤销之间若发生新的登录，新产生的会话族不在撤销范围内——这是既有行为的延续，
     * 不构成一致性快照保证。</p>
     */
    void revokeFamilies(Long userId, Collection<String> familyIds, Instant revokedAt);

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
