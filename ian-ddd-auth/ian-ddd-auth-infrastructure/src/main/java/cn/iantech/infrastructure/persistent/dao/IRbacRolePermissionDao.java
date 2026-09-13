package cn.iantech.infrastructure.persistent.dao;

import cn.iantech.infrastructure.persistent.po.RbacRolePermissionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IRbacRolePermissionDao {

    List<Long> selectPermissionIdsByRoleId(@Param("accountId") Long accountId, @Param("roleId") Long roleId);

    int deleteByRoleIdAndPermissionIds(@Param("accountId") Long accountId, @Param("roleId") Long roleId, @Param("permissionIds") List<Long> permissionIds);

    int batchInsert(@Param("list") List<RbacRolePermissionPO> list);

    int deleteAllByRoleId(@Param("accountId") Long accountId, @Param("roleId") Long roleId);

    int deleteAllByPermissionId(@Param("accountId") Long accountId, @Param("permissionId") Long permissionId);

}
