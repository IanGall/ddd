package cn.iantech.domain.channel.infra;

import cn.iantech.domain.channel.model.ChannelCredentialEntity;

import java.util.List;
import java.util.Optional;

public interface IChannelCredentialRepository {

    ChannelCredentialEntity save(ChannelCredentialEntity entity);

    Optional<ChannelCredentialEntity> findById(Long id);

    Optional<ChannelCredentialEntity> findByChannelCode(String channelCode);

    boolean existsByChannelCode(String channelCode);

    long countPage(String channelCode, String channelName, Boolean status);

    List<ChannelCredentialEntity> queryPage(String channelCode, String channelName,
                                            Boolean status, int offset, int pageSize);

    int updateName(Long id, String channelName, Long updatedByUserId);

    int updateStatus(Long id, Boolean status, Long updatedByUserId);

    int rotateSecret(Long id, Long expectedVersion, byte[] ciphertext, byte[] iv,
                     String keyId, Long newVersion, Long updatedByUserId);

    int logicDelete(Long id, Long updatedByUserId);
}
