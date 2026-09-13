package cn.iantech.infrastructure.persistent.repository;

import cn.iantech.domain.channel.infra.IChannelDataScopeRepository;
import cn.iantech.domain.channel.model.ChannelDataScope;
import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.infrastructure.persistent.dao.IChannelDataScopeDao;
import cn.iantech.infrastructure.persistent.po.ChannelDataScopePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChannelDataScopeRepository implements IChannelDataScopeRepository {
    private final IChannelDataScopeDao dao;
    private final GlobalIdGenerator globalIdGenerator;

    @Override
    public List<ChannelDataScope> findEnabledByChannelId(Long channelId, String scopeType) {
        return dao.selectEnabled(channelId, scopeType).stream()
                .map(item -> ChannelDataScope.builder().channelId(item.getChannelId())
                        .scopeType(item.getScopeType()).scopeValue(item.getScopeValue()).status(item.getStatus())
                        .version(item.getVersion()).createdByUserId(item.getCreatedByUserId())
                        .updatedByUserId(item.getUpdatedByUserId()).build())
                .toList();
    }

    @Override
    public void replace(Long channelId, String scopeType, List<String> scopeValues, Long operatorUserId) {
        dao.deleteByChannelAndType(channelId, scopeType);
        if (!scopeValues.isEmpty()) {
            List<ChannelDataScopePO> scopes = scopeValues.stream()
                    .map(scopeValue -> toPo(channelId, scopeType, scopeValue, operatorUserId))
                    .toList();
            dao.insertBatch(scopes);
        }
    }

    private ChannelDataScopePO toPo(Long channelId, String scopeType, String scopeValue, Long operatorUserId) {
        ChannelDataScopePO po = new ChannelDataScopePO();
        po.setId(globalIdGenerator.nextId());
        po.setChannelId(channelId);
        po.setScopeType(scopeType);
        po.setScopeValue(scopeValue);
        po.setStatus(true);
        po.setVersion(1L);
        po.setCreatedByUserId(operatorUserId);
        po.setUpdatedByUserId(operatorUserId);
        po.setDeleted(false);
        return po;
    }
}
