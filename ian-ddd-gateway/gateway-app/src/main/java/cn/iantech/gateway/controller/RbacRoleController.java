package cn.iantech.gateway.controller;

import cn.iantech.api.model.rbac.*;
import cn.iantech.common.model.Response;
import cn.iantech.gateway.model.RbacWebRequests;
import cn.iantech.gateway.service.GatewayRbacClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iantech.gateway.model.GatewayResponses.success;

@Validated
@RestController
@RequestMapping("/api/admin/rbac/roles")
public class RbacRoleController {

    private final GatewayRbacClient rbacClient;

    public RbacRoleController(GatewayRbacClient rbacClient) {
        this.rbacClient = rbacClient;
    }

    @PostMapping
    public Response<RbacRoleDTO> createRole(@Valid @RequestBody RbacWebRequests.CreateRole request) {
        CreateRbacRoleReq rpcRequest = CreateRbacRoleReq.builder()
                .roleCode(request.roleCode())
                .roleName(request.roleName())
                .roleDesc(request.roleDesc())
                .status(request.status())
                .build();
        return success(rbacClient.createRole(rpcRequest));
    }

    @GetMapping("/{id}")
    public Response<RbacRoleDTO> queryRoleById(@Positive(message = "角色ID必须大于0") @PathVariable("id") Long id) {
        return success(rbacClient.queryRoleById(id));
    }

    @GetMapping
    public Response<RbacRolePageDTO> queryRolePage(
            @Min(value = 1, message = "页码必须大于0") @RequestParam(defaultValue = "1") Integer pageNum,
            @Min(value = 1, message = "页大小必须大于0") @Max(value = 100, message = "页大小不能超过100")
            @RequestParam(defaultValue = "20") Integer pageSize,
            @Size(max = 64, message = "角色编码长度不能超过64") @RequestParam(required = false) String roleCode,
            @Size(max = 128, message = "角色名称长度不能超过128") @RequestParam(required = false) String roleName,
            @RequestParam(required = false) Boolean status) {
        QueryRbacRolePageReq rpcRequest = QueryRbacRolePageReq.builder()
                .pageNum(pageNum)
                .pageSize(pageSize)
                .roleCode(roleCode)
                .roleName(roleName)
                .status(status)
                .build();
        return success(rbacClient.queryRolePage(rpcRequest));
    }

    @PutMapping("/{id}")
    public Response<RbacRoleDTO> updateRole(
            @Positive(message = "角色ID必须大于0") @PathVariable("id") Long id,
            @Valid @RequestBody RbacWebRequests.UpdateRole request) {
        UpdateRbacRoleReq rpcRequest = UpdateRbacRoleReq.builder()
                .id(id)
                .roleCode(request.roleCode())
                .roleName(request.roleName())
                .roleDesc(request.roleDesc())
                .status(request.status())
                .build();
        return success(rbacClient.updateRole(rpcRequest));
    }

    @DeleteMapping("/{id}")
    public Response<Boolean> deleteRole(@Positive(message = "角色ID必须大于0") @PathVariable("id") Long id) {
        return success(rbacClient.deleteRole(DeleteRbacRoleReq.builder().id(id).build()));
    }

}
