package cn.iantech.gateway.e2e;

import cn.iantech.coverage.junit.CoversE2e;
import cn.iantech.gateway.support.CoverageE2eSupport;
import cn.iantech.gateway.support.CoverageE2eSupport.AdminCredential;
import cn.iantech.gateway.support.CoverageE2eSupport.Tokens;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关错误语义 E2E 覆盖率测试。
 *
 * <p>覆盖认证失败、权限拒绝、参数校验失败、资源不存在与状态冲突等异常分支，
 * 这些分支是 {@code GatewayExceptionHandler} 与过滤器的主要覆盖缺口。</p>
 */
@CoversE2e(value = "gateway-error")
@EnabledIfEnvironmentVariable(named = "RUN_COVERAGE_E2E", matches = "true")
class GatewayErrorE2eTest {

    private static Tokens tokens;

    /**
     * 当前管理员完整登录名，用于推导子账号登录所需的账号ID部分。
     */
    private static String adminLoginName;

    @BeforeAll
    static void login() {
        AdminCredential admin = CoverageE2eSupport.provisionAdmin();
        adminLoginName = admin.loginName();
        tokens = CoverageE2eSupport.adminLogin(admin.loginName(), admin.password());
    }

    @Test
    void shouldRejectUnauthenticatedAndMalformedTokens() {
        // 完全不带令牌
        assertEquals(401, CoverageE2eSupport.get("/api/admin/rbac/users").expectStatus(401).status());
        // 非 Bearer 格式
        assertEquals("AUTH_REQUIRED", CoverageE2eSupport.get("/api/admin/rbac/users", "not-a-bearer-token")
                .expectStatus(401).errorCode());
        // 伪造令牌
        assertEquals("AUTH_REQUIRED", CoverageE2eSupport.get("/api/admin/rbac/users", "forged-token-value")
                .expectStatus(401).errorCode());
    }

    @Test
    void shouldRejectInvalidRequestBodies() {
        // 缺少必填字段
        CoverageE2eSupport.post("/api/admin/rbac/roles", "{\"roleName\":\"缺少编码\"}", tokens.accessToken())
                .expectError(400, "INVALID_ARGUMENT");
        // 非法邮箱格式
        CoverageE2eSupport.post("/api/admin/rbac/users", """
                        {"username":"e2e_bad_%s","password":"E2e@123456","email":"not-an-email"}"""
                        .formatted(CoverageE2eSupport.uniqueSuffix()), tokens.accessToken())
                .expectError(400, "INVALID_ARGUMENT");
        // 非法 JSON
        CoverageE2eSupport.post("/api/admin/rbac/roles", "{not-json", tokens.accessToken())
                .expectError(400, "INVALID_ARGUMENT");
        // 路径变量类型不匹配
        CoverageE2eSupport.get("/api/admin/rbac/users/abc", tokens.accessToken())
                .expectError(400, "INVALID_ARGUMENT");
        // 越界分页参数
        CoverageE2eSupport.get("/api/admin/rbac/users?pageNum=0", tokens.accessToken())
                .expectError(400, "INVALID_ARGUMENT");
    }

    @Test
    void shouldRejectNonPrimarySubjectOnChannelManagement() {
        // 子账号无「仅平台主账号」资格，必须被拒绝
        String suffix = CoverageE2eSupport.uniqueSuffix();
        String subUsername = "e2e_sub_" + suffix;
        String subPassword = "E2e@123456" + suffix;
        JsonNode subUser = CoverageE2eSupport.post("/api/admin/rbac/users", """
                {"username":"%s","password":"%s","displayName":"E2E 受限子账号"}"""
                .formatted(subUsername, subPassword), tokens.accessToken()).data();

        String accountIdPart = adminLoginName.substring(adminLoginName.indexOf('@') + 1);
        Tokens subTokens = CoverageE2eSupport.adminLogin(subUsername + "@" + accountIdPart, subPassword);

        CoverageE2eSupport.get("/api/admin/platform/channel-credentials", subTokens.accessToken())
                .expectError(403, "ACCESS_DENIED");

        // 清理子账号
        CoverageE2eSupport.delete("/api/admin/rbac/users/" + subUser.path("id").asLong(), tokens.accessToken()).data();
    }

    @Test
    void shouldReturnNotFoundForMissingResources() {
        // RBAC 领域把「不存在」统一按参数不合法上报
        CoverageE2eSupport.get("/api/admin/rbac/users/999999999", tokens.accessToken())
                .expectError(400, "INVALID_ARGUMENT");
        CoverageE2eSupport.get("/api/admin/rbac/roles/999999999", tokens.accessToken())
                .expectError(400, "INVALID_ARGUMENT");
        CoverageE2eSupport.get("/api/admin/rbac/permissions/999999999", tokens.accessToken())
                .expectError(400, "INVALID_ARGUMENT");
    }

    @Test
    void shouldRejectDuplicateCodesAsConflict() {
        String suffix = CoverageE2eSupport.uniqueSuffix();
        String roleBody = """
                {"roleCode":"e2e_dup_%s","roleName":"E2E 重复角色"}""".formatted(suffix);
        long roleId = CoverageE2eSupport.post("/api/admin/rbac/roles", roleBody, tokens.accessToken())
                .data().path("id").asLong();

        // 同一账号内角色编码唯一，重复创建必须失败
        CoverageE2eSupport.post("/api/admin/rbac/roles", roleBody, tokens.accessToken())
                .expectError(400, "INVALID_ARGUMENT");

        CoverageE2eSupport.delete("/api/admin/rbac/roles/" + roleId, tokens.accessToken()).data();
    }

    @Test
    void shouldExposeStatusAndHealthEndpoints() {
        JsonNode status = CoverageE2eSupport.get("/api/admin/status", tokens.accessToken()).data();
        assertEquals("UP", status.path("status").asText(), "网关状态应为 UP");
        assertTrue(!status.path("application").asText().isBlank(), "应返回应用名");

        // 探活分区不走认证，直接返回 actuator 原生结构
        assertEquals("UP", CoverageE2eSupport.readTree(CoverageE2eSupport.get("/actuator/health").body())
                .path("status").asText());
    }
}
