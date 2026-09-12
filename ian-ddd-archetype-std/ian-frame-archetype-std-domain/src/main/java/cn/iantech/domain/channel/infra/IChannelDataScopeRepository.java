package cn.iantech.domain.channel.infra;

import cn.iantech.domain.channel.model.ChannelDataScope;

import java.util.List;

public interface IChannelDataScopeRepository {
    List<ChannelDataScope> findEnabledByChannelId(Long channelId, String scopeType);

    void replace(Long channelId, String scopeType, List<String> scopeValues, Long operatorUserId);
}
