package cn.iantech.trigger.convertor;

import cn.iantech.api.model.channel.ChannelCredentialDTO;
import cn.iantech.api.model.channel.ChannelCredentialSecretDTO;
import cn.iantech.api.model.channel.ChannelDataScopeDTO;
import cn.iantech.domain.channel.model.ChannelCredentialEntity;
import cn.iantech.domain.channel.model.ChannelDataScope;
import cn.iantech.domain.channel.model.IssuedChannelCredential;

/**
 * 渠道领域对象与 RPC DTO 的边界转换器。
 */
public final class ChannelApiConverter {

    private ChannelApiConverter() {
    }

    public static ChannelCredentialDTO toDTO(ChannelCredentialEntity entity) {
        return ChannelCredentialDTO.builder().id(entity.getId()).channelCode(entity.getChannelCode())
                .channelName(entity.getChannelName()).secretVersion(entity.getSecretVersion()).status(entity.getStatus())
                .lastRotatedAt(entity.getLastRotatedAt()).createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime()).build();
    }

    public static ChannelCredentialSecretDTO toSecretDTO(IssuedChannelCredential issued) {
        return ChannelCredentialSecretDTO.builder().id(issued.credential().getId())
                .channelCode(issued.credential().getChannelCode()).channelSecret(issued.channelSecret())
                .secretVersion(issued.credential().getSecretVersion()).build();
    }

    public static ChannelDataScopeDTO toScopeDTO(ChannelDataScope scope) {
        return ChannelDataScopeDTO.builder().scopeType(scope.scopeType()).scopeValue(scope.scopeValue()).build();
    }
}
