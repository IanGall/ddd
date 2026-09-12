package cn.iantech.domain.auth.infra;

import java.time.Duration;

/**
 * Auth 登录风险状态端口。调用方只传递不可逆摘要，不向 Redis 暴露原始 IP 和登录名。
 */
public interface IAuthRiskStore {

    boolean allowIpAttempt(String ipAddressHash, int maximumAttempts, Duration window);

    boolean isLoginBlocked(String loginNameHash, int maximumFailures);

    boolean recordLoginFailure(String loginNameHash, int maximumFailures, Duration lockDuration);

    void clearLoginFailures(String loginNameHash);
}
