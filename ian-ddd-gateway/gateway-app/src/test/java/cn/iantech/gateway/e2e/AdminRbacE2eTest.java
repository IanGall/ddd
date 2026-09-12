package cn.iantech.gateway.e2e;

import cn.iantech.coverage.junit.CoversE2e;
import cn.iantech.gateway.support.CoverageE2eSupport;
import cn.iantech.gateway.support.CoverageE2eSupport.AdminCredential;
import cn.iantech.gateway.support.CoverageE2eSupport.Tokens;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理端 RBAC 全生命周期 E2E 覆盖率测试。
 *
 * <p>依次走通「权限 → 角色 → 角色绑定权限 → 用户 → 用户绑定角色 → 回读校验 → 清理」
 * 的完整链路，覆盖 Gateway 控制器、触发层 RPC、用例服务与领域服务。</p>
 */
@CoversE2e(value = "admin-rbac")
@EnabledIfEnvironmentVariable(named = "RUN_COVERAGE_E2E", matches = "true")
class AdminRbacE2eTest {

    private static Tokens tokens;

    /**
     * 主账号完整登录名，形如 用户名@账号ID.com，子账号登录时需复用其中的账号ID部分。
     */
    private static String primaryLoginName;

    @BeforeAll
    static void login() {
        AdminCredential admin = CoverageE2eSupport.resolveAdmin();
        primaryLoginName = admin.loginName();
        tokens = CoverageE2eSupport.adminLogin(admin.loginName(), admin.password());
    }

    /**
     * 查询开户时自动初始化的系统权限ID（{@code rbac:*} 前缀由系统管理，不允许自定义创建）。
     */
    private static long systemPermissionId(String permCode) {
        JsonNode page = CoverageE2eSupport.get(
                "/api/admin/rbac/permissions?pageNum=1&pageSize=50&permCode=" + permCode, tokens.accessToken()).data();
        for (JsonNode permission : page.path("list")) {
            if (permCode.equals(permission.path("permCode").asText())) {
                return permission.path("id").asLong();
            }
        }
        throw new IllegalStateException("未找到系统权限：" + permCode);
    }

    private static List<Long> toLongList(JsonNode array) {
        List<Long> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asLong()));
        return values;
    }

    @Test
    void shouldCompletePermissionRoleUserLifecycle() {
        String suffix = CoverageE2eSupport.uniqueSuffix();

        // 1. 创建权限
        JsonNode permission = CoverageE2eSupport.post("/api/admin/rbac/permissions", """
                        {"permCode":"e2e:perm:%s","permName":"E2E 权限 %s","permType":3,
                         "path":"/api/e2e/%s","method":"GET"}""".formatted(suffix, suffix, suffix), tokens.accessToken())
                .data();
        long permissionId = permission.path("id").asLong();
        assertEquals("e2e:perm:" + suffix, permission.path("permCode").asText());
        assertTrue(permission.path("status").asBoolean(), "默认状态应为启用");

        // 2. 创建角色
        JsonNode role = CoverageE2eSupport.post("/api/admin/rbac/roles", """
                {"roleCode":"e2e_role_%s","roleName":"E2E 角色 %s","roleDesc":"覆盖率测试角色"}"""
                .formatted(suffix, suffix), tokens.accessToken()).data();
        long roleId = role.path("id").asLong();
        assertEquals("e2e_role_" + suffix, role.path("roleCode").asText());

        // 3. 角色绑定权限并回读
        CoverageE2eSupport.put("/api/admin/rbac/roles/" + roleId + "/permissions",
                "{\"permissionIds\":[%d]}".formatted(permissionId), tokens.accessToken()).data();
        JsonNode rolePermissionIds = CoverageE2eSupport.get("/api/admin/rbac/roles/" + roleId + "/permissions",
                tokens.accessToken()).data();
        assertEquals(List.of(permissionId), toLongList(rolePermissionIds.path("permissionIds")));

        // 4. 创建用户
        JsonNode user = CoverageE2eSupport.post("/api/admin/rbac/users", """
                {"username":"e2e_user_%s","password":"E2e@12345%s","displayName":"E2E 用户 %s",
                 "email":"e2e_%s@example.com","mobile":"1390000%s"}"""
                .formatted(suffix, suffix, suffix, suffix, suffix), tokens.accessToken()).data();
        long userId = user.path("id").asLong();
        assertEquals("e2e_user_" + suffix, user.path("username").asText());

        // 5. 用户绑定角色并回读
        CoverageE2eSupport.put("/api/admin/rbac/users/" + userId + "/roles",
                "{\"roleIds\":[%d]}".formatted(roleId), tokens.accessToken()).data();
        JsonNode userRoleIds = CoverageE2eSupport.get("/api/admin/rbac/users/" + userId + "/roles",
                tokens.accessToken()).data();
        assertEquals(List.of(roleId), toLongList(userRoleIds.path("roleIds")));

        // 6. 分页与详情查询
        JsonNode userPage = CoverageE2eSupport.get(
                "/api/admin/rbac/users?pageNum=1&pageSize=10&username=e2e_user_" + suffix, tokens.accessToken()).data();
        assertTrue(userPage.path("total").asLong() >= 1, "按用户名应能检索到刚创建的用户");
        assertEquals(userId, CoverageE2eSupport.get("/api/admin/rbac/users/" + userId, tokens.accessToken())
                .data().path("id").asLong());

        JsonNode rolePage = CoverageE2eSupport.get(
                "/api/admin/rbac/roles?pageNum=1&pageSize=10&roleCode=e2e_role_" + suffix, tokens.accessToken()).data();
        assertTrue(rolePage.path("total").asLong() >= 1, "按角色编码应能检索到刚创建的角色");
        assertEquals(roleId, CoverageE2eSupport.get("/api/admin/rbac/roles/" + roleId, tokens.accessToken())
                .data().path("id").asLong());

        // 7. 更新三类实体
        JsonNode updatedUser = CoverageE2eSupport.put("/api/admin/rbac/users/" + userId,
                """
                        {"displayName":"E2E 用户已更新","email":"updated_%s@example.com","status":false}"""
                        .formatted(suffix), tokens.accessToken()).data();
        assertEquals("E2E 用户已更新", updatedUser.path("displayName").asText());
        assertEquals(false, updatedUser.path("status").asBoolean());

        JsonNode updatedRole = CoverageE2eSupport.put("/api/admin/rbac/roles/" + roleId, """
                {"roleCode":"e2e_role_%s","roleName":"E2E 角色已更新","roleDesc":"更新后的描述","status":true}"""
                .formatted(suffix), tokens.accessToken()).data();
        assertEquals("E2E 角色已更新", updatedRole.path("roleName").asText());

        JsonNode updatedPermission = CoverageE2eSupport.put("/api/admin/rbac/permissions/" + permissionId, """
                {"permName":"E2E 权限已更新","permType":2,"path":"/api/e2e/%s","method":"POST"}"""
                .formatted(suffix), tokens.accessToken()).data();
        assertEquals("E2E 权限已更新", updatedPermission.path("permName").asText());
        assertEquals(2, updatedPermission.path("permType").asInt());

        // 8. 清理：解绑关系后删除实体
        CoverageE2eSupport.put("/api/admin/rbac/users/" + userId + "/roles", "{\"roleIds\":[]}",
                tokens.accessToken()).data();
        assertTrue(toLongList(CoverageE2eSupport.get("/api/admin/rbac/users/" + userId + "/roles",
                tokens.accessToken()).data().path("roleIds")).isEmpty(), "解绑后角色列表应为空");

        assertTrue(CoverageE2eSupport.delete("/api/admin/rbac/users/" + userId, tokens.accessToken())
                .data().asBoolean());
        assertTrue(CoverageE2eSupport.delete("/api/admin/rbac/permissions/" + permissionId, tokens.accessToken())
                .data().asBoolean());
        assertTrue(CoverageE2eSupport.delete("/api/admin/rbac/roles/" + roleId, tokens.accessToken())
                .data().asBoolean());
    }

    @Test
    void shouldManagePermissionHierarchyAndPagination() {
        String suffix = CoverageE2eSupport.uniqueSuffix();

        JsonNode parent = CoverageE2eSupport.post("/api/admin/rbac/permissions", """
                {"permCode":"e2e:tree:%s","permName":"E2E 目录 %s","permType":1}"""
                .formatted(suffix, suffix), tokens.accessToken()).data();
        long parentId = parent.path("id").asLong();

        JsonNode child = CoverageE2eSupport.post("/api/admin/rbac/permissions", """
                {"permCode":"e2e:tree:%s:child","permName":"E2E 菜单 %s","permType":2,"parentId":%d}"""
                .formatted(suffix, suffix, parentId), tokens.accessToken()).data();
        assertEquals(parentId, child.path("parentId").asLong());

        JsonNode page = CoverageE2eSupport.get(
                        "/api/admin/rbac/permissions?pageNum=1&pageSize=10&permCode=e2e:tree:" + suffix, tokens.accessToken())
                .data();
        assertTrue(page.path("total").asLong() >= 2, "父子权限都应能被分页检索到");
        assertEquals("e2e:tree:" + suffix,
                CoverageE2eSupport.get("/api/admin/rbac/permissions/" + parentId, tokens.accessToken())
                        .data().path("permCode").asText());

        assertTrue(CoverageE2eSupport.delete("/api/admin/rbac/permissions/" + child.path("id").asLong(),
                tokens.accessToken()).data().asBoolean());
        assertTrue(CoverageE2eSupport.delete("/api/admin/rbac/permissions/" + parentId,
                tokens.accessToken()).data().asBoolean());
    }

    @Test
    void shouldSupportSubAccountLoginWithGrantedRole() {
        String suffix = CoverageE2eSupport.uniqueSuffix();

        // 子账号必须靠系统权限码授权：先把 rbac:user:read 授予新角色，再建用户并绑定
        long userReadPermissionId = systemPermissionId("rbac:user:read");
        JsonNode role = CoverageE2eSupport.post("/api/admin/rbac/roles", """
                {"roleCode":"e2e_sub_role_%s","roleName":"E2E 子账号角色 %s"}"""
                .formatted(suffix, suffix), tokens.accessToken()).data();
        long roleId = role.path("id").asLong();
        CoverageE2eSupport.put("/api/admin/rbac/roles/" + roleId + "/permissions",
                "{\"permissionIds\":[%d]}".formatted(userReadPermissionId), tokens.accessToken()).data();

        String subUsername = "e2e_sub_" + suffix;
        String subPassword = "E2e@12345" + suffix;
        JsonNode subUser = CoverageE2eSupport.post("/api/admin/rbac/users", """
                {"username":"%s","password":"%s","displayName":"E2E 子账号"}"""
                .formatted(subUsername, subPassword), tokens.accessToken()).data();
        long subUserId = subUser.path("id").asLong();
        CoverageE2eSupport.put("/api/admin/rbac/users/" + subUserId + "/roles",
                "{\"roleIds\":[%d]}".formatted(roleId), tokens.accessToken()).data();

        // 子账号登录名格式与主账号一致：用户名@账号ID.com
        String accountIdPart = primaryLoginName.substring(primaryLoginName.indexOf('@') + 1);
        Tokens subTokens = CoverageE2eSupport.adminLogin(subUsername + "@" + accountIdPart, subPassword);
        assertTrue(!subTokens.accessToken().isBlank(), "子账号应签发访问令牌");
        assertEquals("SUB_ACCOUNT", subTokens.userType(), "登录主体应为子账号");

        // 携带 rbac:user:read 授权后可以读用户列表
        JsonNode subUserPage = CoverageE2eSupport.get("/api/admin/rbac/users?pageNum=1&pageSize=5",
                subTokens.accessToken()).data();
        assertTrue(subUserPage.path("total").asLong() >= 1);

        // 未授权的角色管理必须被拒绝，验证授权边界
        CoverageE2eSupport.get("/api/admin/rbac/roles?pageNum=1&pageSize=5", subTokens.accessToken())
                .expectError(403, "ACCESS_DENIED");

        CoverageE2eSupport.put("/api/admin/rbac/users/" + subUserId + "/roles", "{\"roleIds\":[]}",
                tokens.accessToken()).data();
    }
}
