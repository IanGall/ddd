package cn.iantech.infrastructure.persistent.dao;

import cn.iantech.infrastructure.persistent.po.RbacUserPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IRbacUserDao {

    int insert(RbacUserPO rbacUserPO);

    RbacUserPO selectById(@Param("accountId") Long accountId, @Param("id") Long id);

    RbacUserPO selectByUsername(@Param("accountId") Long accountId, @Param("username") String username);

    long selectPageCount(@Param("accountId") Long accountId, @Param("username") String username, @Param("status") Boolean status);

    List<RbacUserPO> selectPage(@Param("accountId") Long accountId,
                                @Param("username") String username,
                                @Param("status") Boolean status,
                                @Param("offset") Integer offset,
                                @Param("pageSize") Integer pageSize);

    int updateById(@Param("accountId") Long accountId, @Param("item") RbacUserPO rbacUserPO);

    int logicDeleteById(@Param("accountId") Long accountId, @Param("id") Long id);

}
