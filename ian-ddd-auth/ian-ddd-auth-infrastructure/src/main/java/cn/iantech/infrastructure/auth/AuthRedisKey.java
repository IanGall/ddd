package cn.iantech.infrastructure.auth;

import cn.iantech.domain.auth.model.AuthTokenFormat;
import cn.iantech.redis.RedisKeyBuilder;
import cn.iantech.redis.RedisKeyScope;

/**
 * Auth Redis 键构造器，统一约束会话 Cluster Slot 与动态键片段。
 */
final class AuthRedisKey {

    /**
     * 会话命名空间版本跟随 Token 格式版本，避免两处版本常量漂移导致旧 Token 路由到错误命名空间。
     */
    static final String SESSION_NAMESPACE = "auth:session:" + AuthTokenFormat.VERSION;
    static final String RISK_NAMESPACE = "auth:risk:v1";

    private AuthRedisKey() {
    }

    static String scopePrefix(Long userId) {
        return RedisKeyBuilder.scopedPrefix(SESSION_NAMESPACE, redisScope(userId)) + ":";
    }

    static String session(Long userId, String sessionId) {
        return RedisKeyBuilder.key(SESSION_NAMESPACE, redisScope(userId), "session", sessionId);
    }

    static String access(Long userId, String tokenHash) {
        return RedisKeyBuilder.key(SESSION_NAMESPACE, redisScope(userId), "access", tokenHash);
    }

    static String refresh(Long userId, String tokenHash) {
        return RedisKeyBuilder.key(SESSION_NAMESPACE, redisScope(userId), "refresh", tokenHash);
    }

    static String family(Long userId, String familyId) {
        return RedisKeyBuilder.key(SESSION_NAMESPACE, redisScope(userId), "family", familyId);
    }

    static String user(Long userId) {
        return scopePrefix(userId) + "user";
    }

    static String riskIp(String ipAddressHash) {
        return RedisKeyBuilder.key(RISK_NAMESPACE, "ip", ipAddressHash);
    }

    static String riskLogin(String loginNameHash) {
        return RedisKeyBuilder.key(RISK_NAMESPACE, "login", loginNameHash);
    }

    private static RedisKeyScope redisScope(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        return RedisKeyBuilder.scope(userId);
    }
}
