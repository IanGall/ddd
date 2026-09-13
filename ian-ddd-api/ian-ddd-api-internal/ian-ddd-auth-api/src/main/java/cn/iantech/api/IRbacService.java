package cn.iantech.api;

import cn.iantech.api.model.rbac.*;
import cn.iantech.common.exception.AppException;

public interface IRbacService {
    RbacUserDTO createUser(CreateRbacUserReq req) throws AppException;

    RbacUserDTO queryUserById(Long id) throws AppException;

    RbacUserPageDTO queryUserPage(QueryRbacUserPageReq req) throws AppException;

    RbacUserDTO updateUser(UpdateRbacUserReq req) throws AppException;

    Boolean deleteUser(DeleteRbacUserReq req) throws AppException;

    RbacRoleDTO createRole(CreateRbacRoleReq req) throws AppException;

    RbacRoleDTO queryRoleById(Long id) throws AppException;

    RbacRolePageDTO queryRolePage(QueryRbacRolePageReq req) throws AppException;

    RbacRoleDTO updateRole(UpdateRbacRoleReq req) throws AppException;

    Boolean deleteRole(DeleteRbacRoleReq req) throws AppException;

    RbacPermissionDTO createPermission(CreateRbacPermissionReq req) throws AppException;

    RbacPermissionDTO queryPermissionById(Long id) throws AppException;

    RbacPermissionPageDTO queryPermissionPage(QueryRbacPermissionPageReq req) throws AppException;

    RbacPermissionDTO updatePermission(UpdateRbacPermissionReq req) throws AppException;

    Boolean deletePermission(DeleteRbacPermissionReq req) throws AppException;

    Boolean replaceUserRoles(ReplaceUserRolesReq req) throws AppException;

    Boolean replaceRolePermissions(ReplaceRolePermissionsReq req) throws AppException;

    QueryUserRoleIdsResp queryUserRoleIds(QueryUserRoleIdsReq req) throws AppException;

    QueryRolePermissionIdsResp queryRolePermissionIds(QueryRolePermissionIdsReq req) throws AppException;

}
