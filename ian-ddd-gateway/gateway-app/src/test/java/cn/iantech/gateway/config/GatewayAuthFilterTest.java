package cn.iantech.gateway.config;

import cn.iantech.api.model.auth.AuthIdentityDTO;
import cn.iantech.api.model.auth.AuthSubjectTypes;
import cn.iantech.common.exception.AppException;
import cn.iantech.context.core.ContextAccessor;
import cn.iantech.context.core.RequestContext;
import cn.iantech.gateway.service.GatewayAuthClient;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class GatewayAuthFilterTest {

    @Test
    void shouldAllowOnlyMatchingBearerSubjectForAdminAndAppPartitions() throws Exception {
        assertBearerDecision("/api/admin", adminIdentity(), true);
        assertBearerDecision("/api/admin", adminPrimaryIdentity(), true);
        assertBearerDecision("/api/admin/", adminIdentity(), true);
        assertBearerDecision("/api/admin/rbac/roles", adminIdentity(), true);
        assertBearerDecision("/api/admin/rbac/roles", customerIdentity(), false);
        assertBearerDecision("/api/admin/rbac/roles", clientIdentity(), false);
        assertBearerDecision("/api/app", customerIdentity(), true);
        assertBearerDecision("/api/app/", customerIdentity(), true);
        assertBearerDecision("/api/app/orders", customerIdentity(), true);
        assertBearerDecision("/api/app/orders", adminIdentity(), false);
        assertBearerDecision("/api/app/orders", clientIdentity(), false);
    }

    @Test
    void shouldAllowAnonymousAndPlatformRoutesWithoutBearerToken() throws Exception {
        GatewayAuthClient authClient = mock(GatewayAuthClient.class);
        GatewayAuthFilter filter = new GatewayAuthFilter(authClient, resolver());

        for (String path : new String[]{"/api/admin/auth/login", "/api/admin/auth/refresh",
                "/api/app/auth/register", "/api/app/auth/login", "/api/app/auth/refresh",
                "/api/admin/platform/accounts"}) {
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(new MockHttpServletRequest("POST", path), new MockHttpServletResponse(), chain);
            verify(chain).doFilter(any(), any());
        }
        FilterChain healthChain = mock(FilterChain.class);
        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"),
                new MockHttpServletResponse(), healthChain);
        verify(healthChain).doFilter(any(), any());
        verifyNoInteractions(authClient);
    }

    @Test
    void shouldNotExposeAnonymousOrPlatformPathsWithWrongMethod() throws Exception {
        GatewayAuthClient authClient = mock(GatewayAuthClient.class);
        HandlerExceptionResolver resolver = resolver();
        GatewayAuthFilter filter = new GatewayAuthFilter(authClient, resolver);

        for (String path : new String[]{"/api/admin/auth/login", "/api/admin/auth/refresh",
                "/api/app/auth/register", "/api/app/auth/login", "/api/app/auth/refresh",
                "/api/admin/platform/accounts"}) {
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), chain);
            verifyNoInteractions(chain);
        }
        FilterChain healthChain = mock(FilterChain.class);
        filter.doFilter(new MockHttpServletRequest("POST", "/actuator/health"),
                new MockHttpServletResponse(), healthChain);
        verifyNoInteractions(healthChain);

        verify(resolver, times(7)).resolveException(any(), any(), isNull(),
                argThat(exception -> exception instanceof AppException));
        verifyNoInteractions(authClient);
    }

    @Test
    void shouldRejectLegacyUnknownAndUnsafePathsByDefault() throws Exception {
        GatewayAuthClient authClient = mock(GatewayAuthClient.class);
        HandlerExceptionResolver resolver = resolver();
        GatewayAuthFilter filter = new GatewayAuthFilter(authClient, resolver);

        String[] deniedPaths = {"/auth/login", "/customer/register", "/platform/accounts",
                "/api/rbac/roles", "/api/mobile/orders", "/api/integration/orders", "/api/unknown",
                "/api/adminx", "/api/application", "/api/external-test", "/api/admin//roles",
                "/api/admin/roles;version=1", "/api/admin/./roles", "/api/admin/../app",
                "/api/admin%2froles", "/api/admin\\roles", "/api/app/%2e%2e/admin"};
        for (String path : deniedPaths) {
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), chain);
            verifyNoInteractions(chain);
        }
        verify(resolver, times(deniedPaths.length)).resolveException(any(), any(), isNull(),
                argThat(exception -> exception instanceof AppException appException
                        && "ACCESS_DENIED".equals(appException.getCode())));
        verifyNoInteractions(authClient);
    }

    @Test
    void shouldRejectIdentityWithoutExplicitSubjectContract() throws Exception {
        GatewayAuthClient authClient = mock(GatewayAuthClient.class);
        when(authClient.validate("legacy-token")).thenReturn(AuthIdentityDTO.builder()
                .accountId(100L).userId(200L).username("operator").userType("SUB_ACCOUNT")
                .sessionId("session-1").build());
        HandlerExceptionResolver resolver = resolver();
        GatewayAuthFilter filter = new GatewayAuthFilter(authClient, resolver);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(bearerRequest("/api/admin/rbac/roles", "legacy-token"),
                new MockHttpServletResponse(), chain);

        verifyNoInteractions(chain);
        verify(resolver).resolveException(any(), any(), isNull(),
                argThat(exception -> exception instanceof AppException appException
                        && "AUTH_REQUIRED".equals(appException.getCode())));
    }

    @Test
    void shouldBuildTrustedContextAndClearItAfterRequest() throws Exception {
        GatewayAuthClient authClient = mock(GatewayAuthClient.class);
        when(authClient.validate("opaque-token")).thenReturn(adminIdentity());
        GatewayAuthFilter filter = new GatewayAuthFilter(authClient, resolver());
        AtomicReference<RequestContext> captured = new AtomicReference<>();

        filter.doFilter(bearerRequest("/api/admin/rbac/roles", "opaque-token"),
                new MockHttpServletResponse(),
                (request, response) -> captured.set(ContextAccessor.current().orElseThrow()));

        assertEquals("ADMIN_SUB_ACCOUNT", captured.get().subjectType());
        assertEquals("100", captured.get().tenantId());
        assertEquals("200", captured.get().userId());
        assertFalse(ContextAccessor.current().isPresent());
    }

    @Test
    void shouldAuthenticateExternalRequestWithChannelHmac() throws Exception {
        GatewayAuthClient authClient = mock(GatewayAuthClient.class);
        when(authClient.authenticateChannel(any())).thenReturn(clientIdentity());
        GatewayAuthFilter filter = new GatewayAuthFilter(authClient, resolver());
        byte[] body = "{\"orderId\":1}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = signedRequest("/api/external/orders", body);
        AtomicReference<RequestContext> context = new AtomicReference<>();
        AtomicReference<String> forwardedBody = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
            context.set(ContextAccessor.current().orElseThrow());
            forwardedBody.set(new String(servletRequest.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        });

        verify(authClient).authenticateChannel(any());
        assertEquals("{\"orderId\":1}", forwardedBody.get());
        assertEquals(AuthSubjectTypes.PLATFORM_CLIENT, context.get().subjectType());
        assertEquals("external:access", context.get().authorizedScope());
    }

    @Test
    void shouldAcceptExternalRootAndTrailingSlashWithHmac() throws Exception {
        GatewayAuthClient authClient = mock(GatewayAuthClient.class);
        when(authClient.authenticateChannel(any())).thenReturn(clientIdentity());
        GatewayAuthFilter filter = new GatewayAuthFilter(authClient, resolver());

        for (String path : new String[]{"/api/external", "/api/external/"}) {
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(signedRequest(path, new byte[0]), new MockHttpServletResponse(), chain);
            verify(chain).doFilter(any(), any());
        }
        verify(authClient, times(2)).authenticateChannel(any());
    }

    @Test
    void shouldRejectNonClientIdentityReturnedByExternalAuthentication() throws Exception {
        for (AuthIdentityDTO identity : new AuthIdentityDTO[]{adminIdentity(), customerIdentity()}) {
            GatewayAuthClient authClient = mock(GatewayAuthClient.class);
            when(authClient.authenticateChannel(any())).thenReturn(identity);
            GatewayAuthFilter filter = new GatewayAuthFilter(authClient, resolver());
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(signedRequest("/api/external/orders", new byte[0]),
                    new MockHttpServletResponse(), chain);

            verifyNoInteractions(chain);
            verify(authClient).authenticateChannel(any());
        }
    }

    private void assertBearerDecision(String path, AuthIdentityDTO identity, boolean allowed) throws Exception {
        GatewayAuthClient authClient = mock(GatewayAuthClient.class);
        when(authClient.validate("opaque-token")).thenReturn(identity);
        GatewayAuthFilter filter = new GatewayAuthFilter(authClient, resolver());
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(bearerRequest(path, "opaque-token"), new MockHttpServletResponse(), chain);

        if (allowed) {
            verify(chain).doFilter(any(), any());
        } else {
            verifyNoInteractions(chain);
        }
    }

    private MockHttpServletRequest bearerRequest(String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private MockHttpServletRequest signedRequest(String path, byte[] body) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setContentType("application/json; charset=UTF-8");
        request.setContent(body);
        request.addHeader(ChannelCanonicalRequest.CHANNEL_CODE_HEADER, "ch_abcdefghijklmnopqrstuv");
        request.addHeader(ChannelCanonicalRequest.SECRET_VERSION_HEADER, "1");
        request.addHeader(ChannelCanonicalRequest.TIMESTAMP_HEADER, "1787107200");
        request.addHeader(ChannelCanonicalRequest.CONTENT_SHA256_HEADER,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)));
        request.addHeader(ChannelCanonicalRequest.SIGNATURE_HEADER, "1".repeat(64));
        return request;
    }

    private HandlerExceptionResolver resolver() {
        HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
        when(resolver.resolveException(any(), any(), isNull(), any())).thenReturn(new ModelAndView());
        return resolver;
    }

    private AuthIdentityDTO adminIdentity() {
        return AuthIdentityDTO.builder().subjectType("ADMIN_SUB_ACCOUNT").subjectId("200")
                .accountId(100L).userId(200L).username("operator").userType("SUB_ACCOUNT")
                .tokenKind("OPAQUE").sessionId("session-1").build();
    }

    private AuthIdentityDTO adminPrimaryIdentity() {
        return AuthIdentityDTO.builder().subjectType("ADMIN_PRIMARY").subjectId("100")
                .accountId(100L).userId(100L).username("root").userType("PRIMARY")
                .tokenKind("OPAQUE").sessionId("session-primary").build();
    }

    private AuthIdentityDTO customerIdentity() {
        return AuthIdentityDTO.builder().subjectType("CUSTOMER").subjectId("300")
                .userId(300L).username("customer").userType("CUSTOMER")
                .tokenKind("OPAQUE").sessionId("session-2").build();
    }

    private AuthIdentityDTO clientIdentity() {
        return AuthIdentityDTO.builder().subjectType(AuthSubjectTypes.PLATFORM_CLIENT).subjectId("ch_abcdefghijklmnopqrstuv")
                .clientId("ch_abcdefghijklmnopqrstuv").tokenKind("CHANNEL_HMAC")
                .credentialVersion(1L).authorizedScope("external:access").build();
    }
}
