package cn.iantech.cases.rbac.model;

import java.util.List;

/**
 * RBAC 用例命令集合。
 */
public final class RbacCaseCommands {
    private RbacCaseCommands() {
    }

    public record CreateAccount(String username, String password, String displayName, String email, String mobile) {
    }

    public record AccountResult(Long accountId, String username, String loginName) {
    }

    public record CreateUser(String username, String password, String displayName, String email, String mobile,
                             Boolean status) {
    }

    public record UpdateUser(Long id, String password, String displayName, String email, String mobile,
                             Boolean status) {
    }

    public record QueryUserPage(Integer pageNum, Integer pageSize, String username, Boolean status) {
    }

    public record CreateRole(String roleCode, String roleName, String roleDesc, Boolean status) {
    }

    public record UpdateRole(Long id, String roleCode, String roleName, String roleDesc, Boolean status) {
    }

    public record QueryRolePage(Integer pageNum, Integer pageSize, String roleCode, String roleName, Boolean status) {
    }

    public record CreatePermission(String permCode, String permName, Integer permType, Long parentId, String path,
                                   String method, Boolean status) {
    }

    public record UpdatePermission(Long id, String permName, Integer permType, Long parentId, String path,
                                   String method, Boolean status) {
    }

    public record QueryPermissionPage(Integer pageNum, Integer pageSize, String permCode, String permName,
                                      Integer permType, Long parentId, Boolean status) {
    }

    public record ReplaceUserRoles(Long userId, List<Long> roleIds) {
    }

    public record ReplaceRolePermissions(Long roleId, List<Long> permissionIds) {
    }
}
