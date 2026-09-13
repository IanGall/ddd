package cn.iantech.infrastructure.persistent.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IRbacAuthorizationDao {

    boolean existsPermission(@Param("accountId") Long accountId,
                             @Param("userId") Long userId,
                             @Param("permissionCode") String permissionCode);

    List<String> findRoleCodes(@Param("accountId") Long accountId, @Param("userId") Long userId);

    List<String> findPermissionCodes(@Param("accountId") Long accountId, @Param("userId") Long userId);

    List<String> findPermissionCodesByRoleIds(@Param("accountId") Long accountId, @Param("roleIds") List<Long> roleIds);

    List<String> findPermissionCodesByIds(@Param("accountId") Long accountId, @Param("permissionIds") List<Long> permissionIds);
}
