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
@RequestMapping("/api/admin/rbac/users")
public class RbacUserController {

    private final GatewayRbacClient rbacClient;

    public RbacUserController(GatewayRbacClient rbacClient) {
        this.rbacClient = rbacClient;
    }

    @PostMapping
    public Response<RbacUserDTO> createUser(@Valid @RequestBody RbacWebRequests.CreateUser request) {
        CreateRbacUserReq rpcRequest = CreateRbacUserReq.builder()
                .username(request.username())
                .password(request.password())
                .displayName(request.displayName())
                .email(request.email())
                .mobile(request.mobile())
                .status(request.status())
                .build();
        return success(rbacClient.createUser(rpcRequest));
    }

    @GetMapping("/{id}")
    public Response<RbacUserDTO> queryUserById(@Positive(message = "用户ID必须大于0") @PathVariable("id") Long id) {
        return success(rbacClient.queryUserById(id));
    }

    @GetMapping
    public Response<RbacUserPageDTO> queryUserPage(
            @Min(value = 1, message = "页码必须大于0") @RequestParam(defaultValue = "1") Integer pageNum,
            @Min(value = 1, message = "页大小必须大于0") @Max(value = 100, message = "页大小不能超过100")
            @RequestParam(defaultValue = "20") Integer pageSize,
            @Size(max = 64, message = "用户名长度不能超过64") @RequestParam(required = false) String username,
            @RequestParam(required = false) Boolean status) {
        QueryRbacUserPageReq rpcRequest = QueryRbacUserPageReq.builder()
                .pageNum(pageNum)
                .pageSize(pageSize)
                .username(username)
                .status(status)
                .build();
        return success(rbacClient.queryUserPage(rpcRequest));
    }

    @PutMapping("/{id}")
    public Response<RbacUserDTO> updateUser(
            @Positive(message = "用户ID必须大于0") @PathVariable("id") Long id,
            @Valid @RequestBody RbacWebRequests.UpdateUser request) {
        UpdateRbacUserReq rpcRequest = UpdateRbacUserReq.builder()
                .id(id)
                .password(request.password())
                .displayName(request.displayName())
                .email(request.email())
                .mobile(request.mobile())
                .status(request.status())
                .build();
        RbacUserDTO updated = rbacClient.updateUser(rpcRequest);
        return success(updated);
    }

    @DeleteMapping("/{id}")
    public Response<Boolean> deleteUser(@Positive(message = "用户ID必须大于0") @PathVariable("id") Long id) {
        Boolean deleted = rbacClient.deleteUser(DeleteRbacUserReq.builder().id(id).build());
        return success(deleted);
    }

}
