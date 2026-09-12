package cn.iantech.gateway.model;

import jakarta.validation.constraints.*;

import java.util.List;

/** HTTP 请求模型，避免将校验注解扩散到 Dubbo API 契约。 */
public final class RbacWebRequests {

    private RbacWebRequests() { }

    public record CreateUser(
            @NotBlank(message = "用户名不能为空") @Size(max = 64, message = "用户名长度不能超过64") String username,
            @NotBlank(message = "密码不能为空") @Size(min = 8, max = 72, message = "密码长度必须为8到72位") String password,
            @Size(max = 128, message = "显示名长度不能超过128") String displayName,
            @Email(message = "邮箱格式不正确") @Size(max = 128, message = "邮箱长度不能超过128") String email,
            @Size(max = 32, message = "手机号长度不能超过32") String mobile,
            Boolean status) { }

    public record UpdateUser(
            @Size(min = 8, max = 72, message = "密码长度必须为8到72位") String password,
            @Size(max = 128, message = "显示名长度不能超过128") String displayName,
            @Email(message = "邮箱格式不正确") @Size(max = 128, message = "邮箱长度不能超过128") String email,
            @Size(max = 32, message = "手机号长度不能超过32") String mobile,
            Boolean status) { }

    public record CreateRole(
            @NotBlank(message = "角色编码不能为空") @Size(max = 64, message = "角色编码长度不能超过64") String roleCode,
            @NotBlank(message = "角色名称不能为空") @Size(max = 128, message = "角色名称长度不能超过128") String roleName,
            @Size(max = 255, message = "角色描述长度不能超过255") String roleDesc,
            Boolean status) { }

    public record UpdateRole(
            @NotBlank(message = "角色编码不能为空") @Size(max = 64, message = "角色编码长度不能超过64") String roleCode,
            @NotBlank(message = "角色名称不能为空") @Size(max = 128, message = "角色名称长度不能超过128") String roleName,
            @Size(max = 255, message = "角色描述长度不能超过255") String roleDesc,
            Boolean status) { }

    public record CreatePermission(
            @NotBlank(message = "权限编码不能为空") @Size(max = 64, message = "权限编码长度不能超过64") String permCode,
            @NotBlank(message = "权限名称不能为空") @Size(max = 128, message = "权限名称长度不能超过128") String permName,
            @NotNull(message = "权限类型不能为空") Integer permType,
            Long parentId,
            @Size(max = 255, message = "路径长度不能超过255") String path,
            @Size(max = 16, message = "请求方法长度不能超过16") String method,
            Boolean status) { }

    public record UpdatePermission(
            @NotBlank(message = "权限名称不能为空") @Size(max = 128, message = "权限名称长度不能超过128") String permName,
            @NotNull(message = "权限类型不能为空") Integer permType,
            Long parentId,
            @Size(max = 255, message = "路径长度不能超过255") String path,
            @Size(max = 16, message = "请求方法长度不能超过16") String method,
            Boolean status) { }

    public record UserRoles(@NotNull(message = "角色ID列表不能为空") @Size(max = 500, message = "单次最多操作500条")
                            List<@Positive(message = "角色ID必须大于0") Long> roleIds) { }

    public record RolePermissions(@NotNull(message = "权限ID列表不能为空") @Size(max = 500, message = "单次最多操作500条")
                                  List<@Positive(message = "权限ID必须大于0") Long> permissionIds) { }

}
