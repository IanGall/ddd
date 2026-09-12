package cn.iantech.infrastructure.persistent.dao;

import cn.iantech.infrastructure.persistent.po.ChannelDataScopePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IChannelDataScopeDao {
    List<ChannelDataScopePO> selectEnabled(@Param("channelId") Long channelId,
                                           @Param("scopeType") String scopeType);

    int deleteByChannelAndType(@Param("channelId") Long channelId, @Param("scopeType") String scopeType);

    int insertBatch(@Param("items") List<ChannelDataScopePO> items);
}
