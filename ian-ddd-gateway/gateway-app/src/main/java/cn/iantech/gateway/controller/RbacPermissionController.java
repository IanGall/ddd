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
@RequestMapping("/api/admin/rbac/permissions")
public class RbacPermissionController {

    private final GatewayRbacClient rbacClient;

    public RbacPermissionController(GatewayRbacClient rbacClient) {
        this.rbacClient = rbacClient;
    }

    @PostMapping
    public Response<RbacPermissionDTO> createPermission(@Valid @RequestBody RbacWebRequests.CreatePermission request) {
        CreateRbacPermissionReq rpcRequest = CreateRbacPermissionReq.builder()
                .permCode(request.permCode())
                .permName(request.permName())
                .permType(request.permType())
                .parentId(request.parentId())
                .path(request.path())
                .method(request.method())
                .status(request.status())
                .build();
        return success(rbacClient.createPermission(rpcRequest));
    }

    @GetMapping("/{id}")
    public Response<RbacPermissionDTO> queryPermissionById(
            @Positive(message = "权限ID必须大于0") @PathVariable("id") Long id) {
        return success(rbacClient.queryPermissionById(id));
    }

    @GetMapping
    public Response<RbacPermissionPageDTO> queryPermissionPage(
            @Min(value = 1, message = "页码必须大于0") @RequestParam(defaultValue = "1") Integer pageNum,
            @Min(value = 1, message = "页大小必须大于0") @Max(value = 100, message = "页大小不能超过100")
            @RequestParam(defaultValue = "20") Integer pageSize,
            @Size(max = 64, message = "权限编码长度不能超过64") @RequestParam(required = false) String permCode,
            @Size(max = 128, message = "权限名称长度不能超过128") @RequestParam(required = false) String permName,
            @RequestParam(required = false) Integer permType,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) Boolean status) {
        QueryRbacPermissionPageReq rpcRequest = QueryRbacPermissionPageReq.builder()
                .pageNum(pageNum)
                .pageSize(pageSize)
                .permCode(permCode)
                .permName(permName)
                .permType(permType)
                .parentId(parentId)
                .status(status)
                .build();
        return success(rbacClient.queryPermissionPage(rpcRequest));
    }

    @PutMapping("/{id}")
    public Response<RbacPermissionDTO> updatePermission(
            @Positive(message = "权限ID必须大于0") @PathVariable("id") Long id,
            @Valid @RequestBody RbacWebRequests.UpdatePermission request) {
        UpdateRbacPermissionReq rpcRequest = UpdateRbacPermissionReq.builder()
                .id(id)
                .permName(request.permName())
                .permType(request.permType())
                .parentId(request.parentId())
                .path(request.path())
                .method(request.method())
                .status(request.status())
                .build();
        return success(rbacClient.updatePermission(rpcRequest));
    }

    @DeleteMapping("/{id}")
    public Response<Boolean> deletePermission(@Positive(message = "权限ID必须大于0") @PathVariable("id") Long id) {
        return success(rbacClient.deletePermission(DeleteRbacPermissionReq.builder().id(id).build()));
    }

}
