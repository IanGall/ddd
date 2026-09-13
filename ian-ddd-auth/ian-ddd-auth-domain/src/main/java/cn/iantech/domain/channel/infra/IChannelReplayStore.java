package cn.iantech.domain.channel.infra;

import java.time.Duration;

public interface IChannelReplayStore {

    boolean markIfAbsent(String replayKey, Duration ttl);
}
