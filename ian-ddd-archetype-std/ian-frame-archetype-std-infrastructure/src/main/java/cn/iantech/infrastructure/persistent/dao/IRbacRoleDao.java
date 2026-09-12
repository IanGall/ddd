package cn.iantech.infrastructure.persistent.dao;

import cn.iantech.infrastructure.persistent.po.RbacRolePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IRbacRoleDao {

    int insert(RbacRolePO rbacRolePO);

    RbacRolePO selectById(@Param("accountId") Long accountId, @Param("id") Long id);

    RbacRolePO selectByRoleCode(@Param("accountId") Long accountId, @Param("roleCode") String roleCode);

    long selectPageCount(@Param("accountId") Long accountId,
                         @Param("roleCode") String roleCode,
                         @Param("roleName") String roleName,
                         @Param("status") Boolean status);

    List<RbacRolePO> selectPage(@Param("accountId") Long accountId,
                                @Param("roleCode") String roleCode,
                                @Param("roleName") String roleName,
                                @Param("status") Boolean status,
                                @Param("offset") Integer offset,
                                @Param("pageSize") Integer pageSize);

    int updateById(@Param("accountId") Long accountId, @Param("item") RbacRolePO rbacRolePO);

    int logicDeleteById(@Param("accountId") Long accountId, @Param("id") Long id);

    List<Long> selectExistingIds(@Param("accountId") Long accountId, @Param("ids") List<Long> ids);

}
