package cn.iantech.gateway;

import cn.iantech.coverage.junit.CoversE2e;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分布式覆盖率端到端测试。
 *
 * <p>需要 Gateway 与标准服务都以 jacocoagent tcpserver 模式运行，并先启动 coverage-controller。
 * 未设置 {@code RUN_COVERAGE_E2E=true} 时自动跳过，避免影响常规构建。</p>
 *
 * <p>启用方式：</p>
 * <pre>
 * # 1. 启动覆盖率控制器
 * mvn -f ddd-base/ian-ddd-coverage/coverage-controller/pom.xml spring-boot:run
 *
 * # 2. 带 jacocoagent 启动标准服务与 Gateway（见各自 README 的覆盖率小节）
 *
 * # 3. 运行本测试
 * RUN_COVERAGE_E2E=true mvn -f ian-ddd-gateway/gateway-app/pom.xml test -Dtest=GatewayCoverageE2eTest
 * </pre>
 *
 * <p>如需覆盖 Dubbo 下游调用链，额外提供管理员账号，登录请求会真实经过
 * Gateway → Dubbo → AuthService：</p>
 *
 * <pre>
 * COVERAGE_E2E_LOGIN_NAME=... COVERAGE_E2E_LOGIN_PASSWORD=... RUN_COVERAGE_E2E=true mvn ...
 * </pre>
 */
@CoversE2e(value = "gateway-e2e")
@EnabledIfEnvironmentVariable(named = "RUN_COVERAGE_E2E", matches = "true")
class GatewayCoverageE2eTest {

    private static final String GATEWAY_BASE_URL =
            System.getProperty("gateway.base-url", "http://127.0.0.1:8092");

    /**
     * 管理员登录名。平台开户接口返回的 loginName 形如 {@code 用户名@accountId.com}，需完整传入。
     */
    private static final String LOGIN_NAME = resolve("coverage.e2e.login-name", "COVERAGE_E2E_LOGIN_NAME");

    private static final String LOGIN_PASSWORD = resolve("coverage.e2e.login-password", "COVERAGE_E2E_LOGIN_PASSWORD");

    /**
     * 优先读系统属性，其次环境变量。
     */
    private static String resolve(String propertyKey, String environmentKey) {
        String value = System.getProperty(propertyKey);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return System.getenv().getOrDefault(environmentKey, "").trim();
    }

    @Test
    void shouldCoverGatewayHealthEndpoint() throws Exception {
        HttpResponse<String> health = get("/actuator/health");

        assertEquals(200, health.statusCode(), "Gateway 健康检查应返回 200");
        assertTrue(health.body().contains("UP"), "Gateway 健康检查应返回 UP");
    }

    @Test
    void shouldCoverStandardServiceThroughDubbo() throws Exception {
        if (LOGIN_NAME.isBlank() || LOGIN_PASSWORD.isBlank()) {
            System.out.println("[Coverage] 未提供 COVERAGE_E2E_LOGIN_NAME/PASSWORD，跳过 Dubbo 调用链场景");
            return;
        }
        HttpResponse<String> login = post("/api/admin/auth/login", """
                {"loginName":"%s","password":"%s","clientType":"WEB"}"""
                .formatted(LOGIN_NAME, LOGIN_PASSWORD));

        assertEquals(200, login.statusCode(), "登录应成功，才能覆盖到 Dubbo 下游；响应: " + login.body());
        assertTrue(login.body().contains("accessToken"), "登录响应应包含会话令牌: " + login.body());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client().send(HttpRequest.newBuilder(URI.create(GATEWAY_BASE_URL + path))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client().send(HttpRequest.newBuilder(URI.create(GATEWAY_BASE_URL + path))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }
}
