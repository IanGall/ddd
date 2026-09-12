package cn.iantech.domain.channel.service;

import cn.iantech.domain.channel.model.ChannelCredentialEntity;
import cn.iantech.domain.channel.model.IssuedChannelCredential;
import cn.iantech.domain.model.DomainPage;

public interface IChannelCredentialDomainService {

    IssuedChannelCredential create(Long userId, String channelName);

    DomainPage<ChannelCredentialEntity> queryPage(int pageNum, int pageSize,
                                    String channelCode, String channelName, Boolean status);

    ChannelCredentialEntity queryById(Long id);

    ChannelCredentialEntity update(Long userId, Long id, String channelName);

    ChannelCredentialEntity updateStatus(Long userId, Long id, Boolean status);

    IssuedChannelCredential rotateSecret(Long userId, Long id);

    boolean delete(Long userId, Long id);
}
