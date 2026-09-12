package cn.iantech.infrastructure.persistent.dao;

import cn.iantech.infrastructure.persistent.po.RbacPermissionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IRbacPermissionDao {

    int insert(RbacPermissionPO rbacPermissionPO);

    RbacPermissionPO selectById(@Param("accountId") Long accountId, @Param("id") Long id);

    RbacPermissionPO selectByPermCode(@Param("accountId") Long accountId, @Param("permCode") String permCode);

    List<RbacPermissionPO> selectAllByAccount(@Param("accountId") Long accountId);

    long selectPageCount(@Param("accountId") Long accountId,
                         @Param("permCode") String permCode,
                         @Param("permName") String permName,
                         @Param("permType") Integer permType,
                         @Param("parentId") Long parentId,
                         @Param("status") Boolean status);

    List<RbacPermissionPO> selectPage(@Param("accountId") Long accountId,
                                      @Param("permCode") String permCode,
                                      @Param("permName") String permName,
                                      @Param("permType") Integer permType,
                                      @Param("parentId") Long parentId,
                                      @Param("status") Boolean status,
                                      @Param("offset") Integer offset,
                                      @Param("pageSize") Integer pageSize);

    int updateById(@Param("accountId") Long accountId, @Param("item") RbacPermissionPO rbacPermissionPO);

    int logicDeleteById(@Param("accountId") Long accountId, @Param("id") Long id);

    List<Long> selectExistingIds(@Param("accountId") Long accountId, @Param("ids") List<Long> ids);

}
