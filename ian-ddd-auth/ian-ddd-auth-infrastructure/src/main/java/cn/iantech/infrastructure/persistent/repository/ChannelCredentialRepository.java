package cn.iantech.infrastructure.persistent.repository;

import cn.iantech.domain.channel.infra.IChannelCredentialRepository;
import cn.iantech.domain.channel.model.ChannelCredentialEntity;
import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.id.GlobalIdGeneratorProvider;
import cn.iantech.infrastructure.id.AuthIdBusiness;
import cn.iantech.infrastructure.persistent.dao.IChannelCredentialDao;
import cn.iantech.infrastructure.persistent.po.ChannelCredentialPO;
import io.github.linpeilie.Converter;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ChannelCredentialRepository implements IChannelCredentialRepository {
    private final IChannelCredentialDao dao;
    private final Converter converter;
    private final GlobalIdGenerator globalIdGenerator;

    public ChannelCredentialRepository(IChannelCredentialDao dao, Converter converter,
                                      GlobalIdGeneratorProvider idGeneratorProvider) {
        this.dao = dao;
        this.converter = converter;
        this.globalIdGenerator = idGeneratorProvider.forBusiness(AuthIdBusiness.CHANNEL_CREDENTIAL.businessName());
    }

    @Override
    public ChannelCredentialEntity save(ChannelCredentialEntity entity) {
        ChannelCredentialPO po = converter.convert(entity, ChannelCredentialPO.class);
        po.setId(globalIdGenerator.nextId());
        dao.insert(po);
        entity.setId(po.getId());
        return entity;
    }

    @Override
    public Optional<ChannelCredentialEntity> findById(Long id) {
        return Optional.ofNullable(dao.selectById(id))
                .map(po -> converter.convert(po, ChannelCredentialEntity.class));
    }

    @Override
    public Optional<ChannelCredentialEntity> findByChannelCode(String channelCode) {
        return Optional.ofNullable(dao.selectByChannelCode(channelCode))
                .map(po -> converter.convert(po, ChannelCredentialEntity.class));
    }

    @Override
    public boolean existsByChannelCode(String channelCode) {
        return dao.countByChannelCode(channelCode) > 0;
    }

    @Override
    public long countPage(String channelCode, String channelName, Boolean status) {
        return dao.selectPageCount(channelCode, channelName, status);
    }

    @Override
    public List<ChannelCredentialEntity> queryPage(String channelCode, String channelName,
                                                   Boolean status, int offset, int pageSize) {
        return Optional.ofNullable(dao.selectPage(channelCode, channelName, status, offset, pageSize))
                .orElse(List.of()).stream()
                .map(po -> converter.convert(po, ChannelCredentialEntity.class))
                .toList();
    }

    @Override
    public int updateName(Long id, String channelName, Long userId) {
        return dao.updateName(id, channelName, userId);
    }

    @Override
    public int updateStatus(Long id, Boolean status, Long userId) {
        return dao.updateStatus(id, status, userId);
    }

    @Override
    public int rotateSecret(Long id, Long expectedVersion, byte[] ciphertext, byte[] iv,
                            String keyId, Long newVersion, Long userId) {
        return dao.rotateSecret(id, expectedVersion, ciphertext, iv, keyId, newVersion, userId);
    }

    @Override
    public int logicDelete(Long id, Long userId) {
        return dao.logicDelete(id, userId);
    }
}
