package cn.iantech.infrastructure.channel;

import cn.iantech.domain.channel.infra.IChannelReplayStore;
import cn.iantech.redis.IRedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 通过 Redis SET NX 原子登记已验签请求，Redis 异常直接向上抛出并关闭请求。
 */
@Component
@RequiredArgsConstructor
public class RedisChannelReplayStore implements IChannelReplayStore {
    private static final String KEY_PREFIX = "channel:hmac:replay:";
    private final IRedisService redisService;

    @Override
    public boolean markIfAbsent(String replayKey, Duration ttl) {
        return redisService.setIfAbsent(KEY_PREFIX + replayKey, "1", ttl);
    }
}
