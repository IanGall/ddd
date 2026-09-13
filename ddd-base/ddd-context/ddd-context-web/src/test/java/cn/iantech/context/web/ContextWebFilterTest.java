package cn.iantech.context.web;

import cn.iantech.context.core.ContextAccessor;
import cn.iantech.context.core.ContextKeys;
import cn.iantech.context.core.RequestContext;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ContextWebFilterTest {

    // 验证可信主体只来自注入的解析器，外部传入的身份请求头一律忽略
    @Test
    void shouldBuildContextFromResolverPrincipalAndIgnoreExternalIdentityHeaders() throws Exception {
        ContextWebFilter filter = new ContextWebFilter(() ->
                new ResolvedAuthenticationContext("test-admin", null, null, null, null, null, null, null));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ContextWebFilter.REQUEST_ID_HEADER, "request-001");
        request.addHeader("X-User-Id", "forged-user");
        request.addHeader("X-Tenant-Id", "forged-tenant");
        request.addHeader("X-Owner-Account-Id", "forged-owner");
        request.addHeader("X-Authorized-Scope", "forged-scope");
        request.addHeader("X-Credential-Version", "forged-version");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<RequestContext> captured = new AtomicReference<>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                captured.set(ContextAccessor.current().orElseThrow()));

        assertEquals("request-001", captured.get().requestId());
        assertEquals("test-admin", captured.get().principalName());
        assertEquals(RequestContext.SOURCE_GATEWAY, captured.get().source());
        assertNull(captured.get().userId());
        assertNull(captured.get().tenantId());
        assertNull(captured.get().ownerAccountId());
        assertNull(captured.get().authorizedScope());
        assertNull(captured.get().credentialVersion());
        assertEquals("request-001", response.getHeader(ContextWebFilter.REQUEST_ID_HEADER));
        assertFalse(ContextAccessor.current().isPresent());
    }

    // 验证外部请求号非法时生成并回写新的请求号
    @Test
    void shouldGenerateNewRequestIdWhenIncomingRequestIdIsInvalid() throws Exception {
        ContextWebFilter filter = new ContextWebFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ContextWebFilter.REQUEST_ID_HEADER, "非法 请求号");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<RequestContext> captured = new AtomicReference<>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                captured.set(ContextAccessor.current().orElseThrow()));

        assertNotNull(captured.get().requestId());
        assertFalse(captured.get().requestId().isBlank());
        assertEquals(captured.get().requestId(), response.getHeader(ContextWebFilter.REQUEST_ID_HEADER));
        assertFalse(ContextAccessor.current().isPresent());
    }

    // 验证注入的解析器可以恢复 64 位主账号与子账号身份，伪造请求头不会参与解析
    @Test
    void shouldUseResolverTenantAndIgnoreForgedTenantHeader() throws Exception {
        ContextWebFilter configuredFilter = new ContextWebFilter(() ->
                new ResolvedAuthenticationContext(
                        "operator", "9223372036854775807", "9223372036854775806",
                        "ADMIN_PRIMARY", "channel-a", "9223372036854775805", "external:access", "7"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "9999");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<RequestContext> captured = new AtomicReference<>();

        configuredFilter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                captured.set(ContextAccessor.current().orElseThrow()));

        assertEquals("operator", captured.get().principalName());
        assertEquals("9223372036854775807", captured.get().tenantId());
        assertEquals("9223372036854775806", captured.get().userId());
        assertEquals("ADMIN_PRIMARY", captured.get().subjectType());
        assertEquals("channel-a", captured.get().clientId());
        assertEquals("9223372036854775805", captured.get().ownerAccountId());
        assertEquals("external:access", captured.get().authorizedScope());
        assertEquals("7", captured.get().credentialVersion());
    }

    // 验证默认解析器不提供任何身份，伪造身份请求头不会把匿名请求变成可信主体
    @Test
    void shouldResolveAnonymousContextByDefault() throws Exception {
        ContextWebFilter filter = new ContextWebFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "forged-user");
        request.addHeader("X-Tenant-Id", "forged-tenant");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<RequestContext> captured = new AtomicReference<>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                captured.set(ContextAccessor.current().orElseThrow()));

        assertNull(captured.get().principalName());
        assertNull(captured.get().userId());
        assertNull(captured.get().tenantId());
        assertNull(captured.get().subjectType());
        assertNull(captured.get().clientId());
        assertNull(captured.get().ownerAccountId());
        assertNull(captured.get().authorizedScope());
        assertNull(captured.get().credentialVersion());
        assertFalse(ContextAccessor.current().isPresent());
    }

    // 验证请求号写入日志 MDC，并在请求结束后清理
    @Test
    void shouldBindRequestIdToMdcDuringRequestAndClearAfterwards() throws Exception {
        ContextWebFilter filter = new ContextWebFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ContextWebFilter.REQUEST_ID_HEADER, "request-mdc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> duringRequest = new AtomicReference<>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                duringRequest.set(MDC.get(ContextKeys.TRACE_ID)));

        assertEquals("request-mdc", duringRequest.get());
        assertNull(MDC.get(ContextKeys.TRACE_ID));
    }

    // 验证解析器返回空结果时过滤器仍按匿名上下文处理
    @Test
    void shouldFallBackToAnonymousContextWhenResolverReturnsNull() throws Exception {
        ContextWebFilter filter = new ContextWebFilter(() -> null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<RequestContext> captured = new AtomicReference<>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                captured.set(ContextAccessor.current().orElseThrow()));

        assertNull(captured.get().principalName());
        assertFalse(ContextAccessor.current().isPresent());
    }
}
