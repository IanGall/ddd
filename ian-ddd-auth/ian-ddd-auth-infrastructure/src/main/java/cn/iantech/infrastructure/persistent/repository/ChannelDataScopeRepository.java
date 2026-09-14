package cn.iantech.infrastructure.persistent.repository;

import cn.iantech.domain.channel.infra.IChannelDataScopeRepository;
import cn.iantech.domain.channel.model.ChannelDataScope;
import cn.iantech.infrastructure.persistent.dao.IChannelDataScopeDao;
import cn.iantech.infrastructure.persistent.po.ChannelDataScopePO;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 渠道数据范围仓储。
 *
 * <p>数据范围是渠道凭证的从属数据：ID 不出服务、不被任何表引用、领域模型也不携带自身 ID，
 * 因此主键交由数据库自增，不需要全局 ID 生成器。
 */
@Repository
public class ChannelDataScopeRepository implements IChannelDataScopeRepository {
    private final IChannelDataScopeDao dao;

    public ChannelDataScopeRepository(IChannelDataScopeDao dao) {
        this.dao = dao;
    }

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
