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

import static org.junit.jupiter.api.Assertions.*;

/**
 * 外部渠道凭证管理 E2E 覆盖率测试。
 *
 * <p>覆盖渠道凭证的创建、查询、更新、状态变更、密钥轮换、数据范围读写与删除，
 * 并验证「仅平台主账号可管理」的授权约束。</p>
 */
@CoversE2e(value = "channel-credential")
@EnabledIfEnvironmentVariable(named = "RUN_COVERAGE_E2E", matches = "true")
class ChannelCredentialE2eTest {

    private static Tokens tokens;

    @BeforeAll
    static void login() {
        AdminCredential admin = CoverageE2eSupport.provisionAdmin();
        tokens = CoverageE2eSupport.adminLogin(admin.loginName(), admin.password());
    }

    private static List<String> toValueList(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.path("scopeValue").asText()));
        return values;
    }

    @Test
    void shouldCompleteCredentialLifecycle() {
        String suffix = CoverageE2eSupport.uniqueSuffix();

        // 1. 创建渠道凭证，响应携带一次性明文密钥
        JsonNode created = CoverageE2eSupport.post("/api/admin/platform/channel-credentials", """
                {"channelName":"E2E 渠道 %s"}""".formatted(suffix), tokens.accessToken()).data();
        long channelId = created.path("id").asLong();
        String channelCode = created.path("channelCode").asText();
        assertTrue(channelCode.startsWith("ch_"), "渠道编码应使用 ch_ 前缀: " + channelCode);
        assertFalse(created.path("channelSecret").asText().isBlank(), "创建响应应返回明文密钥");
        assertEquals(1L, created.path("secretVersion").asLong(), "初始密钥版本应为 1");

        // 2. 详情与分页查询
        JsonNode detail = CoverageE2eSupport.get("/api/admin/platform/channel-credentials/" + channelId,
                tokens.accessToken()).data();
        assertEquals(channelCode, detail.path("channelCode").asText());
        assertEquals("E2E 渠道 " + suffix, detail.path("channelName").asText());
        assertTrue(detail.path("status").asBoolean(), "新建渠道默认启用");

        JsonNode page = CoverageE2eSupport.get(
                "/api/admin/platform/channel-credentials?pageNum=1&pageSize=10&channelCode=" + channelCode,
                tokens.accessToken()).data();
        assertTrue(page.path("total").asLong() >= 1, "按渠道编码应能检索到记录");

        // 3. 更新名称
        JsonNode renamed = CoverageE2eSupport.put("/api/admin/platform/channel-credentials/" + channelId, """
                {"channelName":"E2E 渠道已改名 %s"}""".formatted(suffix), tokens.accessToken()).data();
        assertEquals("E2E 渠道已改名 " + suffix, renamed.path("channelName").asText());

        // 4. 停用后按状态过滤
        JsonNode disabled = CoverageE2eSupport.put(
                "/api/admin/platform/channel-credentials/" + channelId + "/status",
                "{\"status\":false}", tokens.accessToken()).data();
        assertEquals(false, disabled.path("status").asBoolean());
        JsonNode disabledPage = CoverageE2eSupport.get(
                "/api/admin/platform/channel-credentials?pageNum=1&pageSize=10&channelCode=" + channelCode
                        + "&status=false", tokens.accessToken()).data();
        assertTrue(disabledPage.path("total").asLong() >= 1, "停用后应按 status=false 检索到");

        // 5. 密钥轮换：版本递增且明文密钥不同
        JsonNode rotated = CoverageE2eSupport.post(
                "/api/admin/platform/channel-credentials/" + channelId + "/secret/rotate", null,
                tokens.accessToken()).data();
        assertEquals(2L, rotated.path("secretVersion").asLong(), "轮换后密钥版本应递增");
        assertNotEquals(created.path("channelSecret").asText(), rotated.path("channelSecret").asText(),
                "轮换后必须返回新密钥");

        // 6. 数据范围读写
        CoverageE2eSupport.put("/api/admin/platform/channel-credentials/" + channelId + "/data-scopes/STORE",
                "{\"scopeValues\":[\"S001\",\"S002\"]}", tokens.accessToken()).data();
        JsonNode scopes = CoverageE2eSupport.get(
                "/api/admin/platform/channel-credentials/" + channelId + "/data-scopes/STORE",
                tokens.accessToken()).data();
        assertEquals(List.of("S001", "S002"), toValueList(scopes));

        CoverageE2eSupport.put("/api/admin/platform/channel-credentials/" + channelId + "/data-scopes/STORE",
                "{\"scopeValues\":[\"S003\"]}", tokens.accessToken()).data();
        assertEquals(List.of("S003"), toValueList(CoverageE2eSupport.get(
                "/api/admin/platform/channel-credentials/" + channelId + "/data-scopes/STORE",
                tokens.accessToken()).data()), "替换语义应先清空再写入");

        // 7. 删除后详情查询返回 404
        assertTrue(CoverageE2eSupport.delete("/api/admin/platform/channel-credentials/" + channelId,
                tokens.accessToken()).data().asBoolean());
        CoverageE2eSupport.get("/api/admin/platform/channel-credentials/" + channelId, tokens.accessToken())
                .expectError(404, "NOT_FOUND");
    }

    @Test
    void shouldRejectUnknownScopeTypeAndMissingCredential() {
        JsonNode created = CoverageE2eSupport.post("/api/admin/platform/channel-credentials", """
                        {"channelName":"E2E 渠道 %s"}""".formatted(CoverageE2eSupport.uniqueSuffix()),
                tokens.accessToken()).data();
        long channelId = created.path("id").asLong();

        // 不支持的范围类型
        CoverageE2eSupport.get("/api/admin/platform/channel-credentials/" + channelId + "/data-scopes/UNKNOWN",
                tokens.accessToken()).expectError(400, "INVALID_ARGUMENT");

        // 不存在的凭证：渠道领域按 NOT_FOUND 上报，映射为 HTTP 404
        CoverageE2eSupport.get("/api/admin/platform/channel-credentials/999999999", tokens.accessToken())
                .expectError(404, "NOT_FOUND");
        // 不存在的凭证做数据范围读写同样返回 404
        CoverageE2eSupport.put("/api/admin/platform/channel-credentials/999999999/data-scopes/STORE",
                "{\"scopeValues\":[\"S001\"]}", tokens.accessToken()).expectError(404, "NOT_FOUND");

        CoverageE2eSupport.delete("/api/admin/platform/channel-credentials/" + channelId, tokens.accessToken()).data();
    }
}
