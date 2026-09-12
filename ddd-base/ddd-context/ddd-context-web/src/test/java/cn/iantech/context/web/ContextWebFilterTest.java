package cn.iantech.context.web;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.iantech.context.core.ContextAccessor;
import cn.iantech.context.core.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ContextWebFilterTest {

    private final ContextWebFilter filter = new ContextWebFilter();
    private StpLogic originalStpLogic;
    private TestStpLogic testStpLogic;

    @BeforeEach
    void configureStpLogic() {
        originalStpLogic = StpUtil.getStpLogic();
        testStpLogic = new TestStpLogic();
        StpUtil.setStpLogic(testStpLogic);
    }

    // 每个测试结束后恢复 Sa-Token 逻辑，避免测试之间相互污染
    @AfterEach
    void restoreStpLogic() {
        StpUtil.setStpLogic(originalStpLogic);
    }

    // 验证只从 Sa-Token 登录主体建立上下文，并忽略外部传入的身份请求头
    @Test
    void shouldBuildContextFromAuthenticatedPrincipalAndIgnoreExternalIdentityHeaders() throws Exception {
        testStpLogic.loginId = "test-admin";
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

    // 验证未登录请求不会被误识别为可信主体
    @Test
    void shouldIgnoreAnonymousLoginId() {
        DefaultAuthenticationContextResolver resolver = new DefaultAuthenticationContextResolver();

        ResolvedAuthenticationContext resolved = resolver.resolve();

        assertNull(resolved.principalName());
        assertNull(resolved.tenantId());
        assertNull(resolved.userId());
        assertNull(resolved.subjectType());
        assertNull(resolved.clientId());
        assertNull(resolved.ownerAccountId());
        assertNull(resolved.authorizedScope());
        assertNull(resolved.credentialVersion());
    }

    // 验证非法登录 ID 不会进入请求上下文
    @Test
    void shouldIgnoreInvalidLoginId() {
        testStpLogic.loginId = "非法 登录号";
        DefaultAuthenticationContextResolver resolver = new DefaultAuthenticationContextResolver();

        ResolvedAuthenticationContext resolved = resolver.resolve();

        assertNull(resolved.principalName());
    }

    private static final class TestStpLogic extends StpLogic {

        private String loginId;

        private TestStpLogic() {
            super("test");
        }

        @Override
        public boolean isLogin() {
            return loginId != null;
        }

        @Override
        public String getLoginIdAsString() {
            return loginId;
        }
    }
}
