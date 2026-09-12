package cn.iantech.gateway.controller;

import cn.iantech.api.model.rbac.*;
import cn.iantech.common.model.Response;
import cn.iantech.gateway.model.RbacWebRequests;
import cn.iantech.gateway.service.GatewayRbacClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iantech.gateway.model.GatewayResponses.success;

@Validated
@RestController
@RequestMapping("/api/admin/rbac")
public class RbacRelationController {

    private final GatewayRbacClient rbacClient;

    public RbacRelationController(GatewayRbacClient rbacClient) {
        this.rbacClient = rbacClient;
    }

    @PutMapping("/users/{userId}/roles")
    public Response<Boolean> replaceUserRoles(
            @Positive(message = "用户ID必须大于0") @PathVariable("userId") Long userId,
            @Valid @RequestBody RbacWebRequests.UserRoles request) {
        ReplaceUserRolesReq rpcRequest = ReplaceUserRolesReq.builder()
                .userId(userId)
                .roleIds(request.roleIds())
                .build();
        return success(rbacClient.replaceUserRoles(rpcRequest));
    }

    @GetMapping("/users/{userId}/roles")
    public Response<QueryUserRoleIdsResp> queryUserRoleIds(
            @Positive(message = "用户ID必须大于0") @PathVariable("userId") Long userId) {
        return success(rbacClient.queryUserRoleIds(QueryUserRoleIdsReq.builder().userId(userId).build()));
    }

    @PutMapping("/roles/{roleId}/permissions")
    public Response<Boolean> replaceRolePermissions(
            @Positive(message = "角色ID必须大于0") @PathVariable("roleId") Long roleId,
            @Valid @RequestBody RbacWebRequests.RolePermissions request) {
        ReplaceRolePermissionsReq rpcRequest = ReplaceRolePermissionsReq.builder()
                .roleId(roleId)
                .permissionIds(request.permissionIds())
                .build();
        return success(rbacClient.replaceRolePermissions(rpcRequest));
    }

    @GetMapping("/roles/{roleId}/permissions")
    public Response<QueryRolePermissionIdsResp> queryRolePermissionIds(
            @Positive(message = "角色ID必须大于0") @PathVariable("roleId") Long roleId) {
        return success(rbacClient.queryRolePermissionIds(
                QueryRolePermissionIdsReq.builder().roleId(roleId).build()));
    }

}
