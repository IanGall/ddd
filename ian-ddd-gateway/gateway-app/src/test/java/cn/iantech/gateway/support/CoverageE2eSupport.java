package cn.iantech.gateway.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分布式 E2E 覆盖率测试基座。
 *
 * <p>所有请求真实经过 Gateway → Dubbo → 认证服务，不做任何 Mock，因此被测 JVM 内的
 * JaCoCo Agent 能采集到完整调用链的覆盖率。</p>
 *
 * <p>前置条件：Gateway、认证服务（均带 jacocoagent）与 coverage-controller 已启动。
 * 参见 {@code ddd-base/ian-ddd-coverage/README.md} 的一键流水线。</p>
 */
public final class CoverageE2eSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * Gateway 地址，可通过系统属性 gateway.base-url 覆盖（与既有 E2E 测试保持一致）。
     */
    private static final String BASE_URL = System.getProperty("gateway.base-url", "http://127.0.0.1:8092");

    /**
     * 平台开户令牌：优先取系统属性 coverage.e2e.platform-token，其次取环境变量 COVERAGE_PLATFORM_TOKEN。
     * 不内置默认值，由 coverage-e2e.sh 在运行测试时显式注入（与认证服务 PLATFORM_ADMIN_TOKEN 一致）。
     */
    private static final String PLATFORM_TOKEN = resolvePlatformToken();

    private static String resolvePlatformToken() {
        String token = System.getProperty("coverage.e2e.platform-token");
        if (token == null || token.isBlank()) {
            token = System.getenv("COVERAGE_PLATFORM_TOKEN");
        }
        return token == null ? "" : token;
    }

    private CoverageE2eSupport() {
    }

    public static HttpResult get(String path) {
        return send(request(path).GET());
    }

    public static HttpResult get(String path, String accessToken) {
        return send(bearer(request(path), accessToken).GET());
    }

    public static HttpResult post(String path, String jsonBody) {
        return send(request(path).POST(jsonBody(jsonBody)));
    }

    // ------------------------------------------------------------------
    // HTTP 调用
    // ------------------------------------------------------------------

    public static HttpResult post(String path, String jsonBody, String accessToken) {
        return send(bearer(request(path), accessToken).POST(jsonBody(jsonBody)));
    }

    public static HttpResult put(String path, String jsonBody, String accessToken) {
        return send(bearer(request(path), accessToken).PUT(jsonBody(jsonBody)));
    }

    public static HttpResult delete(String path, String accessToken) {
        return send(bearer(request(path), accessToken).DELETE());
    }

    /**
     * 平台开户接口：使用独立的 X-Platform-Token 头部，不走 Bearer 认证。
     */
    public static HttpResult createPlatformAccount(String username, String password, String displayName) {
        return send(request("/api/admin/platform/accounts")
                .header("X-Platform-Token", PLATFORM_TOKEN)
                .POST(jsonBody("""
                        {"username":"%s","password":"%s","displayName":"%s"}"""
                        .formatted(username, password, displayName))));
    }

    /**
     * 开通一个全新的平台管理员账号，返回可直接登录的凭证。
     *
     * <p>平台开户接口返回的 loginName 形如 {@code 用户名@账号ID.com}，必须完整用于登录。</p>
     */
    public static AdminCredential provisionAdmin() {
        String username = "e2e" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String password = "E2e!" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        JsonNode account = createPlatformAccount(username, password, "E2E 覆盖率账号").data();
        String loginName = account.path("loginName").asText();
        assertTrue(!loginName.isBlank(), "开户响应缺少 loginName: " + account);
        return new AdminCredential(loginName, password);
    }

    /**
     * 管理员登录，返回访问令牌与刷新令牌。
     */
    public static Tokens adminLogin(String loginName, String password) {
        JsonNode data = post("/api/admin/auth/login", """
                {"loginName":"%s","password":"%s","clientType":"WEB"}"""
                .formatted(loginName, password)).data();
        return toTokens(data);
    }

    /**
     * 管理员登录，未提供凭证时自动开户（用于本地交互式运行）。
     */
    public static AdminCredential resolveAdmin() {
        String loginName = firstNonBlank(
                System.getProperty("coverage.e2e.login-name"), System.getenv("COVERAGE_E2E_LOGIN_NAME"));
        String password = firstNonBlank(
                System.getProperty("coverage.e2e.login-password"), System.getenv("COVERAGE_E2E_LOGIN_PASSWORD"));
        if (loginName != null && password != null) {
            return new AdminCredential(loginName, password);
        }
        return provisionAdmin();
    }

    // ------------------------------------------------------------------
    // 账户与令牌
    // ------------------------------------------------------------------

    private static Tokens toTokens(JsonNode data) {
        String accessToken = data.path("accessToken").asText();
        String refreshToken = data.path("refreshToken").asText();
        String sessionId = data.path("sessionId").asText();
        assertTrue(!accessToken.isBlank(), "登录响应缺少 accessToken: " + data);
        assertTrue(!refreshToken.isBlank(), "登录响应缺少 refreshToken: " + data);
        assertNotNull(sessionId, "登录响应缺少 sessionId");
        return new Tokens(accessToken, refreshToken, sessionId, data.path("userType").asText());
    }

    /**
     * 解析 JSON 文本。
     */
    public static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException("响应不是合法 JSON: " + json, exception);
        }
    }

    /**
     * 唯一后缀，避免跨用例数据冲突。
     */
    public static String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    // ------------------------------------------------------------------
    // JSON 工具
    // ------------------------------------------------------------------

    private static HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json; charset=UTF-8");
    }

    private static HttpRequest.Builder bearer(HttpRequest.Builder builder, String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return builder;
        }
        return builder.header("Authorization", "Bearer " + accessToken);
    }

    /**
     * 空请求体（注销、轮换等无 body 的 POST/PUT）。
     */
    private static HttpRequest.BodyPublisher jsonBody(String body) {
        return body == null || body.isBlank()
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
    }

    private static HttpResult send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = CLIENT.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new HttpResult(response.statusCode(), response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("请求被中断", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("请求失败: " + exception.getMessage(), exception);
        }
    }

    /**
     * 一次 HTTP 调用的原始结果。
     *
     * <p>响应结构：业务成功走 {@code {"code":"SUCCESS","data":...}} 且 HTTP 200；
     * 业务失败由 {@code GatewayExceptionHandler} 按响应码映射 HTTP 状态，
     * 因此断言必须同时校验 HTTP 状态与业务码。</p>
     */
    public record HttpResult(int status, String body) {

        /**
         * 断言业务成功并返回 data 节点。
         */
        public JsonNode data() {
            expectStatus(200);
            JsonNode root = readTree(body);
            assertEquals("SUCCESS", root.path("code").asText(), "期望业务成功，响应: " + body);
            return root.path("data");
        }

        /**
         * 断言业务失败（非 SUCCESS）并返回业务错误码，不限定 HTTP 状态。
         */
        public String errorCode() {
            String code = readTree(body).path("code").asText();
            assertTrue(!"SUCCESS".equals(code), "期望业务失败，响应: " + body);
            return code;
        }

        /**
         * 断言失败响应的 HTTP 状态与业务错误码。
         */
        public HttpResult expectError(int expectedStatus, String expectedCode) {
            expectStatus(expectedStatus);
            assertEquals(expectedCode, errorCode(), "业务码不符，响应: " + body);
            return this;
        }

        /**
         * 断言 HTTP 状态码等于期望值。
         */
        public HttpResult expectStatus(int expected) {
            assertEquals(expected, status, "期望 HTTP " + expected + "，实际 " + status + "，响应: " + body);
            return this;
        }
    }

    /**
     * 管理员登录凭证。
     */
    public record AdminCredential(String loginName, String password) {
    }

    /**
     * 登录后持有的令牌。
     */
    public record Tokens(String accessToken, String refreshToken, String sessionId, String userType) {
    }
}
