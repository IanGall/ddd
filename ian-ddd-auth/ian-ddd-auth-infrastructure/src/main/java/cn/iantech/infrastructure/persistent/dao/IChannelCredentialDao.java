package cn.iantech.infrastructure.persistent.dao;

import cn.iantech.infrastructure.persistent.po.ChannelCredentialPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IChannelCredentialDao {
    int insert(ChannelCredentialPO item);

    ChannelCredentialPO selectById(@Param("id") Long id);

    ChannelCredentialPO selectByChannelCode(@Param("channelCode") String channelCode);

    long countByChannelCode(@Param("channelCode") String channelCode);

    long selectPageCount(@Param("channelCode") String channelCode,
                         @Param("channelName") String channelName, @Param("status") Boolean status);

    List<ChannelCredentialPO> selectPage(@Param("channelCode") String channelCode,
                                         @Param("channelName") String channelName,
                                         @Param("status") Boolean status,
                                         @Param("offset") int offset,
                                         @Param("pageSize") int pageSize);

    int updateName(@Param("id") Long id,
                   @Param("channelName") String channelName, @Param("updatedByUserId") Long updatedByUserId);

    int updateStatus(@Param("id") Long id,
                     @Param("status") Boolean status, @Param("updatedByUserId") Long updatedByUserId);

    int rotateSecret(@Param("id") Long id,
                     @Param("expectedVersion") Long expectedVersion, @Param("ciphertext") byte[] ciphertext,
                     @Param("iv") byte[] iv, @Param("keyId") String keyId,
                     @Param("newVersion") Long newVersion, @Param("updatedByUserId") Long updatedByUserId);

    int logicDelete(@Param("id") Long id,
                    @Param("updatedByUserId") Long updatedByUserId);
}
