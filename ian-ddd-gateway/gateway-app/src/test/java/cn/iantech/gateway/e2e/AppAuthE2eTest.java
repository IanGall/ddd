package cn.iantech.gateway.e2e;

import cn.iantech.coverage.junit.CoversE2e;
import cn.iantech.gateway.support.CoverageE2eSupport;
import cn.iantech.gateway.support.CoverageE2eSupport.Tokens;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * C 端注册、登录与自助会话 E2E 覆盖率测试。
 *
 * <p>链路与管理员共用认证领域服务，但走 C 端主体类型分支，覆盖
 * {@code CustomerIdentityAuthenticator} 与 C 端会话管理。</p>
 */
@CoversE2e(value = "app-auth")
@EnabledIfEnvironmentVariable(named = "RUN_COVERAGE_E2E", matches = "true")
class AppAuthE2eTest {

    private static Tokens appLogin(String loginName, String password) {
        JsonNode login = CoverageE2eSupport.post("/api/app/auth/login", """
                {"loginName":"%s","password":"%s","clientType":"APP"}"""
                .formatted(loginName, password)).data();
        return new Tokens(login.path("accessToken").asText(), login.path("refreshToken").asText(),
                login.path("sessionId").asText(), login.path("userType").asText());
    }

    @Test
    void shouldRegisterLoginRefreshAndLogout() {
        String loginName = "e2eapp" + CoverageE2eSupport.uniqueSuffix();
        String password = "E2e@12345" + CoverageE2eSupport.uniqueSuffix();

        // 1. 注册
        JsonNode registered = CoverageE2eSupport.post("/api/app/auth/register", """
                {"loginName":"%s","password":"%s","displayName":"E2E C 端用户"}"""
                .formatted(loginName, password)).data();
        assertEquals(loginName, registered.path("loginName").asText());
        assertTrue(registered.path("status").asBoolean(), "注册后账号应启用");

        // 2. 重复注册必须被拒绝
        assertEquals("INVALID_ARGUMENT", CoverageE2eSupport.post("/api/app/auth/register", """
                {"loginName":"%s","password":"%s","displayName":"重复注册"}"""
                .formatted(loginName, password)).errorCode());

        // 3. 登录
        JsonNode login = CoverageE2eSupport.post("/api/app/auth/login", """
                {"loginName":"%s","password":"%s","clientType":"APP"}"""
                .formatted(loginName, password)).data();
        String accessToken = login.path("accessToken").asText();
        String refreshToken = login.path("refreshToken").asText();
        String sessionId = login.path("sessionId").asText();
        assertEquals("CUSTOMER", login.path("userType").asText());
        assertTrue(!accessToken.isBlank() && !refreshToken.isBlank());

        // 4. 会话列表包含当前会话
        JsonNode sessions = CoverageE2eSupport.get("/api/app/auth/sessions", accessToken).data();
        assertTrue(sessions.isArray() && sessions.size() >= 1, "应返回 C 端会话列表");
        boolean current = false;
        for (JsonNode session : sessions) {
            if (sessionId.equals(session.path("sessionId").asText())) {
                current = session.path("current").asBoolean();
            }
        }
        assertTrue(current, "当前会话应标记 current");

        // 5. 刷新令牌轮换
        JsonNode refreshed = CoverageE2eSupport.post("/api/app/auth/refresh",
                "{\"refreshToken\":\"%s\",\"clientType\":\"APP\"}".formatted(refreshToken)).data();
        assertNotEquals(refreshToken, refreshed.path("refreshToken").asText(), "刷新令牌必须轮换");
        String rotatedAccessToken = refreshed.path("accessToken").asText();

        // 6. 注销当前会话后令牌失效
        CoverageE2eSupport.post("/api/app/auth/logout", null, rotatedAccessToken).data();
        assertEquals(401, CoverageE2eSupport.get("/api/app/auth/sessions", rotatedAccessToken)
                .expectStatus(401).status());
    }

    @Test
    void shouldRejectWrongPasswordAndUnknownCustomer() {
        String loginName = "e2eapp" + CoverageE2eSupport.uniqueSuffix();
        String password = "E2e@12345" + CoverageE2eSupport.uniqueSuffix();
        CoverageE2eSupport.post("/api/app/auth/register", """
                {"loginName":"%s","password":"%s"}""".formatted(loginName, password)).data();

        assertEquals("AUTH_REQUIRED", CoverageE2eSupport.post("/api/app/auth/login", """
                {"loginName":"%s","password":"WrongPass@123","clientType":"APP"}"""
                .formatted(loginName)).errorCode());
        assertEquals("AUTH_REQUIRED", CoverageE2eSupport.post("/api/app/auth/login", """
                {"loginName":"ghost%s","password":"WrongPass@123","clientType":"APP"}"""
                .formatted(CoverageE2eSupport.uniqueSuffix())).errorCode());
    }

    @Test
    void shouldRevokeOtherCustomerSession() {
        String loginName = "e2eapp" + CoverageE2eSupport.uniqueSuffix();
        String password = "E2e@12345" + CoverageE2eSupport.uniqueSuffix();
        CoverageE2eSupport.post("/api/app/auth/register", """
                {"loginName":"%s","password":"%s"}""".formatted(loginName, password)).data();

        Tokens first = appLogin(loginName, password);
        Tokens second = appLogin(loginName, password);
        assertNotEquals(first.sessionId(), second.sessionId());

        CoverageE2eSupport.delete("/api/app/auth/sessions/" + second.sessionId(), first.accessToken()).data();
        assertEquals(401, CoverageE2eSupport.get("/api/app/auth/sessions", second.accessToken())
                .expectStatus(401).status());

        // 未登录令牌不可访问 C 端受保护接口
        assertEquals(401, CoverageE2eSupport.get("/api/app/auth/sessions", null)
                .expectStatus(401).status());
    }
}
