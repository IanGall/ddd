package cn.iantech.gateway.e2e;

import cn.iantech.coverage.junit.CoversE2e;
import cn.iantech.gateway.support.CoverageE2eSupport;
import cn.iantech.gateway.support.CoverageE2eSupport.AdminCredential;
import cn.iantech.gateway.support.CoverageE2eSupport.Tokens;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 管理端认证与会话自助管理 E2E 覆盖率测试。
 *
 * <p>覆盖登录、刷新、会话列表、单会话注销、全量注销的完整生命周期，
 * 以及刷新令牌轮换后旧令牌失效的安全语义。</p>
 */
@CoversE2e(value = "admin-auth")
@EnabledIfEnvironmentVariable(named = "RUN_COVERAGE_E2E", matches = "true")
class AdminAuthE2eTest {

    private static AdminCredential admin;

    @BeforeAll
    static void prepare() {
        admin = CoverageE2eSupport.resolveAdmin();
    }

    @Test
    void shouldRotateRefreshTokenAndRevokeFamilyOnReplay() {
        Tokens first = CoverageE2eSupport.adminLogin(admin.loginName(), admin.password());

        JsonNode refreshed = CoverageE2eSupport.post("/api/admin/auth/refresh",
                "{\"refreshToken\":\"%s\",\"clientType\":\"WEB\"}".formatted(first.refreshToken())).data();
        String rotatedRefreshToken = refreshed.path("refreshToken").asText();
        String rotatedAccessToken = refreshed.path("accessToken").asText();
        String rotatedSessionId = refreshed.path("sessionId").asText();
        assertTrue(!rotatedRefreshToken.isBlank(), "刷新应返回新的刷新令牌");
        assertNotEquals(first.refreshToken(), rotatedRefreshToken, "刷新令牌必须轮换");
        assertNotEquals(first.sessionId(), rotatedSessionId, "刷新会轮换出新的会话ID");

        // 新访问令牌可用，且会话列表中只有新会话
        JsonNode sessions = CoverageE2eSupport.get("/api/admin/auth/sessions", rotatedAccessToken).data();
        assertTrue(sessions.isArray() && !sessions.isEmpty(), "应能列出当前账号的会话");

        // 旧刷新令牌重放：必须失败，且按重放防护撤销整个令牌族
        assertEquals("AUTH_REQUIRED", CoverageE2eSupport.post("/api/admin/auth/refresh",
                "{\"refreshToken\":\"%s\",\"clientType\":\"WEB\"}".formatted(first.refreshToken())).errorCode());
        assertEquals(401, CoverageE2eSupport.get("/api/admin/auth/sessions", rotatedAccessToken)
                .expectStatus(401).status(), "重放检测应撤销该设备令牌族");
    }

    @Test
    void shouldListAndRevokeSessions() {
        Tokens primary = CoverageE2eSupport.adminLogin(admin.loginName(), admin.password());
        Tokens secondary = CoverageE2eSupport.adminLogin(admin.loginName(), admin.password());
        assertNotEquals(primary.sessionId(), secondary.sessionId(), "两次登录应产生不同会话");

        JsonNode sessions = CoverageE2eSupport.get("/api/admin/auth/sessions", primary.accessToken()).data();
        boolean foundSecondary = false;
        boolean foundCurrent = false;
        for (JsonNode session : sessions) {
            if (secondary.sessionId().equals(session.path("sessionId").asText())) {
                foundSecondary = true;
            }
            if (primary.sessionId().equals(session.path("sessionId").asText())) {
                foundCurrent = true;
                assertTrue(session.path("current").asBoolean(), "当前会话应标记 current");
            }
        }
        assertTrue(foundSecondary, "会话列表应包含另一会话");
        assertTrue(foundCurrent, "会话列表应包含当前会话");

        // 注销另一个会话后，该会话的访问令牌立即失效
        CoverageE2eSupport.delete("/api/admin/auth/sessions/" + secondary.sessionId(), primary.accessToken()).data();
        assertEquals(401, CoverageE2eSupport.get("/api/admin/auth/sessions", secondary.accessToken())
                .expectStatus(401).status());
    }

    @Test
    void shouldLogoutCurrentSessionAndRejectReuse() {
        Tokens tokens = CoverageE2eSupport.adminLogin(admin.loginName(), admin.password());

        CoverageE2eSupport.post("/api/admin/auth/logout", null, tokens.accessToken()).data();
        assertEquals(401, CoverageE2eSupport.get("/api/admin/auth/sessions", tokens.accessToken())
                .expectStatus(401).status());
    }

    @Test
    void shouldLogoutAllSessions() {
        Tokens first = CoverageE2eSupport.adminLogin(admin.loginName(), admin.password());
        Tokens second = CoverageE2eSupport.adminLogin(admin.loginName(), admin.password());

        CoverageE2eSupport.post("/api/admin/auth/logout-all", null, first.accessToken()).data();

        assertEquals(401, CoverageE2eSupport.get("/api/admin/auth/sessions", first.accessToken())
                .expectStatus(401).status());
        assertEquals(401, CoverageE2eSupport.get("/api/admin/auth/sessions", second.accessToken())
                .expectStatus(401).status());

        // 全量注销后可重新登录，确认账号未被锁定
        Tokens again = CoverageE2eSupport.adminLogin(admin.loginName(), admin.password());
        assertFalse(again.accessToken().isBlank());
    }

    @Test
    void shouldRejectWrongPasswordAndUnknownAccount() {
        // 密码错误与账号不存在都必须返回统一的 AUTH_REQUIRED，避免账号枚举
        assertEquals("AUTH_REQUIRED", CoverageE2eSupport.post("/api/admin/auth/login", """
                {"loginName":"%s","password":"WrongPass@123","clientType":"WEB"}"""
                .formatted(admin.loginName())).errorCode());

        assertEquals("AUTH_REQUIRED", CoverageE2eSupport.post("/api/admin/auth/login", """
                {"loginName":"nobody@404.com","password":"Whatever@123","clientType":"WEB"}"""
        ).errorCode());
    }
}
