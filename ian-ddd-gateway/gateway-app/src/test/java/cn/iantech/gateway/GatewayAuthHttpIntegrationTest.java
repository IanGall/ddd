package cn.iantech.gateway;

import cn.iantech.api.model.auth.AuthIdentityDTO;
import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.context.core.ContextAccessor;
import cn.iantech.context.core.RequestContext;
import cn.iantech.gateway.config.GatewayAuthFilter;
import cn.iantech.gateway.service.GatewayAuthClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "dubbo.registry.address=N/A",
                "dubbo.registry.username=test-user",
                "dubbo.registry.password=test-password",
                "dubbo.registry.use-as-metadata-center=false",
                "dubbo.config-center.address=N/A",
                "dubbo.application.metadata-type=local",
                "dubbo.application.metadata-service-protocol=injvm",
                "dubbo.consumer.init=false"
        })
@Import(GatewayAuthHttpIntegrationTest.ContextController.class)
class GatewayAuthHttpIntegrationTest {

    @MockitoBean
    private GatewayAuthClient authClient;

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        reset(authClient);
    }

    @Test
    void shouldReturnUnifiedUnauthorizedResponseWithRequestId() throws Exception {
        HttpResponse<String> response = request("/api/admin/test/context", null, "request-001");

        assertEquals(401, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().contains("application/json"));
        assertEquals("request-001", response.headers().firstValue("X-Request-Id").orElseThrow());
        assertTrue(response.body().contains("\"code\":\"AUTH_REQUIRED\""));
        assertTrue(response.body().contains("\"info\":\"需要认证\""));
    }

    @Test
    void shouldReturnUnifiedAuthUnavailableResponse() throws Exception {
        when(authClient.validate("opaque-token")).thenThrow(new AppException(
                Constants.ResponseCode.AUTH_UNAVAILABLE.getCode(), Constants.ResponseCode.AUTH_UNAVAILABLE.getInfo()));

        HttpResponse<String> response = request("/api/admin/test/context", "opaque-token", "request-002");

        assertEquals(503, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().contains("application/json"));
        assertEquals("request-002", response.headers().firstValue("X-Request-Id").orElseThrow());
        assertTrue(response.body().contains("\"code\":\"AUTH_UNAVAILABLE\""));
    }

    @Test
    void shouldReturnUnifiedAuthRateLimitedResponse() throws Exception {
        when(authClient.validate("rate-limited-token")).thenThrow(new AppException(
                Constants.ResponseCode.AUTH_RATE_LIMITED.getCode(),
                Constants.ResponseCode.AUTH_RATE_LIMITED.getInfo()));

        HttpResponse<String> response = request("/api/admin/test/context", "rate-limited-token", "request-429");

        assertEquals(429, response.statusCode());
        assertEquals("request-429", response.headers().firstValue("X-Request-Id").orElseThrow());
        assertTrue(response.body().contains("\"code\":\"AUTH_RATE_LIMITED\""));
    }

    @Test
    void shouldPropagateValidatedIdentityAndRequestId() throws Exception {
        when(authClient.validate("opaque-token")).thenReturn(AuthIdentityDTO.builder()
                .subjectType("ADMIN_SUB_ACCOUNT").subjectId("200").tokenKind("OPAQUE")
                .accountId(100L).userId(200L).username("operator").userType("SUB_ACCOUNT")
                .sessionId("session-1").build());

        HttpResponse<String> response = request("/api/admin/test/context", "opaque-token", "request-003");

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().contains("application/json"));
        assertEquals("request-003", response.headers().firstValue("X-Request-Id").orElseThrow());
        assertTrue(response.body().contains("\"principalName\":\"operator\""));
        assertTrue(response.body().contains("\"tenantId\":\"100\""));
        assertTrue(response.body().contains("\"userId\":\"200\""));
        assertTrue(response.body().contains("\"source\":\"gateway\""));
    }

    private HttpResponse<String> request(String path, String token, String requestId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header(GatewayAuthFilter.REQUEST_ID_HEADER, requestId)
                .GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @RestController
    static class ContextController {

        @GetMapping("/api/admin/test/context")
        RequestContext context() {
            return ContextAccessor.current().orElseThrow();
        }
    }
}
