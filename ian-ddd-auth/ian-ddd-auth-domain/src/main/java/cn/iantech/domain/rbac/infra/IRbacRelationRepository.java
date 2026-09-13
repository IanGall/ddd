package cn.iantech.domain.rbac.infra;

import java.util.List;

public interface IRbacRelationRepository {

    List<Long> queryRoleIdsByUserId(Long accountId, Long userId);

    int deleteUserRoles(Long accountId, Long userId, List<Long> roleIds);

    int insertUserRoles(Long accountId, Long userId, List<Long> roleIds);

    List<Long> queryPermissionIdsByRoleId(Long accountId, Long roleId);

    int deleteRolePermissions(Long accountId, Long roleId, List<Long> permissionIds);

    int insertRolePermissions(Long accountId, Long roleId, List<Long> permissionIds);

    int deleteAllUserRoles(Long accountId, Long userId);

    int deleteAllRoleRelations(Long accountId, Long roleId);

    int deleteAllPermissionRelations(Long accountId, Long permissionId);

}
