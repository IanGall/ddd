package cn.iantech.domain.rbac.service;

import cn.iantech.domain.model.DomainPage;
import cn.iantech.domain.rbac.model.entity.RbacPermissionEntity;
import cn.iantech.domain.rbac.model.entity.RbacRoleEntity;
import cn.iantech.domain.rbac.model.entity.RbacUserEntity;

import java.util.List;

public interface IRbacDomainService {

    RbacUserEntity createUser(Long accountId, String username, String rawPassword, String displayName, String email, String mobile, Boolean status);

    RbacUserEntity queryUserById(Long accountId, Long id);

    DomainPage<RbacUserEntity> queryUserPage(Long accountId, Integer pageNum, Integer pageSize, String username, Boolean status);

    RbacUserEntity updateUser(Long accountId, Long id, String rawPassword, String displayName, String email, String mobile, Boolean status);

    boolean deleteUser(Long accountId, Long id);

    RbacRoleEntity createRole(Long accountId, String roleCode, String roleName, String roleDesc, Boolean status);

    RbacRoleEntity queryRoleById(Long accountId, Long id);

    DomainPage<RbacRoleEntity> queryRolePage(Long accountId, Integer pageNum, Integer pageSize, String roleCode, String roleName, Boolean status);

    RbacRoleEntity updateRole(Long accountId, Long id, String roleCode, String roleName, String roleDesc, Boolean status);

    boolean deleteRole(Long accountId, Long id);

    RbacPermissionEntity createPermission(Long accountId,
                                          String permCode,
                                          String permName,
                                          Integer permType,
                                          Long parentId,
                                          String path,
                                          String method,
                                          Boolean status);

    RbacPermissionEntity queryPermissionById(Long accountId, Long id);

    DomainPage<RbacPermissionEntity> queryPermissionPage(Long accountId,
                                                 Integer pageNum,
                                                 Integer pageSize,
                                                 String permCode,
                                                 String permName,
                                                 Integer permType,
                                                 Long parentId,
                                                 Boolean status);

    RbacPermissionEntity updatePermission(Long accountId,
                                          Long id,
                                          String permName,
                                          Integer permType,
                                          Long parentId,
                                          String path,
                                          String method,
                                          Boolean status);

    boolean deletePermission(Long accountId, Long id);

    boolean replaceUserRoles(Long accountId, Long userId, List<Long> roleIds);

    boolean replaceRolePermissions(Long accountId, Long roleId, List<Long> permissionIds);

    List<Long> queryUserRoleIds(Long accountId, Long userId);

    List<Long> queryRolePermissionIds(Long accountId, Long roleId);

}
