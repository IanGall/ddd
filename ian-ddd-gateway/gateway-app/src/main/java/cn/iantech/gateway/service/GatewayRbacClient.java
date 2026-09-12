package cn.iantech.gateway.service;

import cn.iantech.api.IPlatformAccountService;
import cn.iantech.api.IRbacService;
import cn.iantech.api.model.rbac.*;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

import static cn.iantech.common.constant.Constants.ResponseCode.RPC_ERROR;

/**
 * 网关到 RBAC 管理服务的 RPC 适配器，统一隔离 Dubbo 异常。
 */
@Component
public class GatewayRbacClient {

    @DubboReference(version = "1.0.0", protocol = "tri", timeout = 3000, retries = 0, check = false)
    private IRbacService rbacService;

    @DubboReference(version = "1.0.0", protocol = "tri", timeout = 3000, retries = 0, check = false)
    private IPlatformAccountService platformAccountService;

    public RbacAccountDTO createAccount(PlatformCreateAccountReq request) {
        return invoke(() -> platformAccountService.createAccount(request));
    }

    public RbacUserDTO createUser(CreateRbacUserReq request) {
        return invoke(() -> rbacService.createUser(request));
    }

    public RbacUserDTO queryUserById(Long id) {
        return invoke(() -> rbacService.queryUserById(id));
    }

    public RbacUserPageDTO queryUserPage(QueryRbacUserPageReq request) {
        return invoke(() -> rbacService.queryUserPage(request));
    }

    public RbacUserDTO updateUser(UpdateRbacUserReq request) {
        return invoke(() -> rbacService.updateUser(request));
    }

    public Boolean deleteUser(DeleteRbacUserReq request) {
        return invoke(() -> rbacService.deleteUser(request));
    }

    public RbacRoleDTO createRole(CreateRbacRoleReq request) {
        return invoke(() -> rbacService.createRole(request));
    }

    public RbacRoleDTO queryRoleById(Long id) {
        return invoke(() -> rbacService.queryRoleById(id));
    }

    public RbacRolePageDTO queryRolePage(QueryRbacRolePageReq request) {
        return invoke(() -> rbacService.queryRolePage(request));
    }

    public RbacRoleDTO updateRole(UpdateRbacRoleReq request) {
        return invoke(() -> rbacService.updateRole(request));
    }

    public Boolean deleteRole(DeleteRbacRoleReq request) {
        return invoke(() -> rbacService.deleteRole(request));
    }

    public RbacPermissionDTO createPermission(CreateRbacPermissionReq request) {
        return invoke(() -> rbacService.createPermission(request));
    }

    public RbacPermissionDTO queryPermissionById(Long id) {
        return invoke(() -> rbacService.queryPermissionById(id));
    }

    public RbacPermissionPageDTO queryPermissionPage(QueryRbacPermissionPageReq request) {
        return invoke(() -> rbacService.queryPermissionPage(request));
    }

    public RbacPermissionDTO updatePermission(UpdateRbacPermissionReq request) {
        return invoke(() -> rbacService.updatePermission(request));
    }

    public Boolean deletePermission(DeleteRbacPermissionReq request) {
        return invoke(() -> rbacService.deletePermission(request));
    }

    public Boolean replaceUserRoles(ReplaceUserRolesReq request) {
        return invoke(() -> rbacService.replaceUserRoles(request));
    }

    public Boolean replaceRolePermissions(ReplaceRolePermissionsReq request) {
        return invoke(() -> rbacService.replaceRolePermissions(request));
    }

    public QueryUserRoleIdsResp queryUserRoleIds(QueryUserRoleIdsReq request) {
        return invoke(() -> rbacService.queryUserRoleIds(request));
    }

    public QueryRolePermissionIdsResp queryRolePermissionIds(QueryRolePermissionIdsReq request) {
        return invoke(() -> rbacService.queryRolePermissionIds(request));
    }

    private <T> T invoke(Supplier<T> invocation) {
        return RpcCallGuard.call(invocation, RPC_ERROR);
    }
}
