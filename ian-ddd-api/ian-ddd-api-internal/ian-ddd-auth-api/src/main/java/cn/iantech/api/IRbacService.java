package cn.iantech.api;

import cn.iantech.api.model.rbac.*;
import cn.iantech.common.exception.AppException;

import java.util.List;

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

    /**
     * 查询当前认证主体的有效权限码集合（去重、升序）。
     *
     * <p>主体取自可信请求上下文，无入参。主账号返回账号内**全部**权限码（含自定义权限，不过滤权限状态），
     * 子账号返回其角色聚合结果；每次调用实时读库，因此撤销角色/权限或停用账号、用户即时生效。</p>
     *
     * <p><b>这是权限引导端点，不校验调用者自身的权限码</b>：若要求某个权限码会形成循环依赖，
     * 没有 RBAC 读权限的子账号将无法加载自己的权限集合（前端表现为菜单全空）。</p>
     */
    List<String> queryOwnPermissionCodes() throws AppException;

}
