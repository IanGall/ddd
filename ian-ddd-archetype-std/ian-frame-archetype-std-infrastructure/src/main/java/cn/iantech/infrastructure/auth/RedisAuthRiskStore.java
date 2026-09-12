package cn.iantech.infrastructure.auth;

import cn.iantech.domain.auth.infra.IAuthRiskStore;
import cn.iantech.redis.IRedisService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Redis 登录风控状态。键名仅包含调用方提供的 SHA-256 摘要。
 */
@Service
public class RedisAuthRiskStore implements IAuthRiskStore {

    private static final String RATE_LIMIT_SCRIPT = """
            local attempts = redis.call('INCR', KEYS[1])
            if attempts == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            if attempts > tonumber(ARGV[1]) then
                return 0
            end
            return 1
            """;
    private static final String FAILURE_SCRIPT = """
            local failures = redis.call('INCR', KEYS[1])
            if failures == 1 or failures >= tonumber(ARGV[1]) then
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            if failures >= tonumber(ARGV[1]) then
                return 1
            end
            return 0
            """;

    private final IRedisService redisService;

    public RedisAuthRiskStore(IRedisService redisService) {
        this.redisService = redisService;
    }

    @Override
    public boolean allowIpAttempt(String ipAddressHash, int maximumAttempts, Duration window) {
        Long result = redisService.executeLongScript(RATE_LIMIT_SCRIPT, List.of(AuthRedisKey.riskIp(ipAddressHash)),
                List.of(maximumAttempts, window.toMillis()));
        return result != null && result == 1L;
    }

    @Override
    public boolean isLoginBlocked(String loginNameHash, int maximumFailures) {
        String failures = redisService.getString(AuthRedisKey.riskLogin(loginNameHash));
        return failures != null && Long.parseLong(failures) >= maximumFailures;
    }

    @Override
    public boolean recordLoginFailure(String loginNameHash, int maximumFailures, Duration lockDuration) {
        Long result = redisService.executeLongScript(FAILURE_SCRIPT, List.of(AuthRedisKey.riskLogin(loginNameHash)),
                List.of(maximumFailures, lockDuration.toMillis()));
        return result != null && result == 1L;
    }

    @Override
    public void clearLoginFailures(String loginNameHash) {
        redisService.remove(AuthRedisKey.riskLogin(loginNameHash));
    }
}
