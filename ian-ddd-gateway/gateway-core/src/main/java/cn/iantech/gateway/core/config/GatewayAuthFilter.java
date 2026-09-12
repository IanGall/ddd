package cn.iantech.gateway.core.config;

import cn.iantech.api.model.auth.AuthIdentityDTO;
import cn.iantech.api.model.auth.AuthSubjectTypes;
import cn.iantech.api.model.channel.ChannelSignatureVerifyReq;
import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.context.core.ContextAccessor;
import cn.iantech.context.core.ContextScope;
import cn.iantech.context.core.ContextValidator;
import cn.iantech.context.core.RequestContext;
import cn.iantech.gateway.core.service.GatewayAuthClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

/**
 * 按固定 API 分区选择认证机制，校验可信主体并建立请求上下文。
 *
 * <p>由 {@code GatewayCoreAutoConfiguration} 以 Bean 形式注册（过滤器顺序在注册器中指定），
 * 不依赖组件扫描，因此对任意包名的接入工程都生效。</p>
 */
public class GatewayAuthFilter extends OncePerRequestFilter {

    public static final String IDENTITY_ATTRIBUTE = GatewayAuthFilter.class.getName() + ".identity";
    public static final String ACCESS_TOKEN_ATTRIBUTE = GatewayAuthFilter.class.getName() + ".accessToken";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final GatewayAuthClient authClient;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public GatewayAuthFilter(GatewayAuthClient authClient,
                             @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.authClient = authClient;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    public static String accessToken(HttpServletRequest request) {
        Object value = request.getAttribute(ACCESS_TOKEN_ATTRIBUTE);
        return value == null ? null : value.toString();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = validOrGenerate(request.getHeader(REQUEST_ID_HEADER));
        response.setHeader(REQUEST_ID_HEADER, requestId);
        RouteKind routeKind = classifyRoute(request);
        switch (routeKind) {
            case ANONYMOUS, PLATFORM, ACTUATOR -> continueAnonymous(request, response, filterChain, requestId);
            case ADMIN, APP -> authenticateBearer(request, response, filterChain, requestId, routeKind);
            case EXTERNAL -> authenticateExternal(request, response, filterChain, requestId);
            case DENIED -> resolveException(request, response,
                    new AppException(Constants.ResponseCode.ACCESS_DENIED.getCode(), "请求路径不在允许的 API 分区内"));
        }
    }

    private void continueAnonymous(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain,
                                   String requestId) throws ServletException, IOException {
        try (ContextScope ignored = ContextAccessor.open(new RequestContext(requestId, null, null, null,
                null, null, null, RequestContext.SOURCE_GATEWAY, null, null, null, null))) {
            filterChain.doFilter(request, response);
        }
    }

    private void authenticateBearer(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain,
                                    String requestId, RouteKind routeKind) throws IOException, ServletException {
        String accessToken = bearerToken(request.getHeader("Authorization"));
        if (accessToken == null) {
            resolveException(request, response, authRequired("需要认证"));
            return;
        }

        AuthIdentityDTO identity;
        try {
            identity = authClient.validate(accessToken);
        } catch (RuntimeException exception) {
            resolveException(request, response, exception);
            return;
        }
        if (!validIdentity(identity)) {
            resolveException(request, response, authRequired("认证令牌无效或已过期"));
            return;
        }
        if (!allowedRoute(routeKind, identity)) {
            resolveException(request, response,
                    new AppException(Constants.ResponseCode.ACCESS_DENIED.getCode(), "主体类型无权访问该 API 分区"));
            return;
        }

        request.setAttribute(IDENTITY_ATTRIBUTE, identity);
        request.setAttribute(ACCESS_TOKEN_ATTRIBUTE, accessToken);
        continueWithIdentity(request, response, filterChain, requestId, identity);
    }

    private void authenticateExternal(HttpServletRequest request, HttpServletResponse response,
                                      FilterChain filterChain, String requestId) throws IOException, ServletException {
        try {
            CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request,
                    ChannelCanonicalRequest.MAX_BODY_BYTES);
            ChannelCanonicalRequest.Material material = ChannelCanonicalRequest.create(wrapped, wrapped.body());
            ChannelSignatureVerifyReq rpcRequest = new ChannelSignatureVerifyReq();
            rpcRequest.setChannelCode(material.channelCode());
            rpcRequest.setSecretVersion(material.secretVersion());
            rpcRequest.setTimestamp(material.timestamp());
            rpcRequest.setSignature(material.signature());
            rpcRequest.setCanonicalRequest(material.canonicalRequest());
            AuthIdentityDTO identity = authClient.authenticateChannel(rpcRequest);
            if (!validIdentity(identity)) {
                resolveException(request, response, authRequired("渠道认证失败"));
                return;
            }
            if (!allowedRoute(RouteKind.EXTERNAL, identity)) {
                resolveException(request, response,
                        new AppException(Constants.ResponseCode.ACCESS_DENIED.getCode(), "主体类型无权访问该 API 分区"));
                return;
            }
            wrapped.setAttribute(IDENTITY_ATTRIBUTE, identity);
            continueWithIdentity(wrapped, response, filterChain, requestId, identity);
        } catch (CachedBodyHttpServletRequest.ChannelPayloadTooLargeException exception) {
            resolveException(request, response, exception);
        } catch (RuntimeException exception) {
            resolveException(request, response, exception);
        }
    }

    private void continueWithIdentity(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain,
                                      String requestId, AuthIdentityDTO identity) throws IOException, ServletException {
        RequestContext context = new RequestContext(requestId, identity.getUsername(),
                stringValue(identity.getAccountId()), stringValue(identity.getUserId()), identity.getSubjectType(),
                identity.getClientId(), null, RequestContext.SOURCE_GATEWAY, null, stringValue(identity.getOwnerAccountId()),
                identity.getAuthorizedScope(), stringValue(identity.getCredentialVersion()));
        try (ContextScope ignored = ContextAccessor.open(context)) {
            filterChain.doFilter(request, response);
        }
    }

    private RouteKind classifyRoute(HttpServletRequest request) {
        String path = safePath(request);
        if (path == null) {
            return RouteKind.DENIED;
        }
        if ("GET".equals(request.getMethod()) && path.equals("/actuator/health")) {
            return RouteKind.ACTUATOR;
        }
        if ("POST".equals(request.getMethod()) && path.equals("/api/admin/platform/accounts")) {
            return RouteKind.PLATFORM;
        }
        if (isAnonymous(request.getMethod(), path)) {
            return RouteKind.ANONYMOUS;
        }
        if (matches(path, "/api/admin")) {
            return RouteKind.ADMIN;
        }
        if (matches(path, "/api/app")) {
            return RouteKind.APP;
        }
        if (matches(path, "/api/external")) {
            return RouteKind.EXTERNAL;
        }
        return RouteKind.DENIED;
    }

    private boolean isAnonymous(String method, String path) {
        return "POST".equals(method) && (path.equals("/api/admin/auth/login") || path.equals("/api/admin/auth/refresh")
                || path.equals("/api/app/auth/register") || path.equals("/api/app/auth/login")
                || path.equals("/api/app/auth/refresh"));
    }

    private String safePath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (requestUri == null || contextPath == null || !requestUri.startsWith(contextPath)) {
            return null;
        }
        String path = requestUri.substring(contextPath.length());
        if (path.isEmpty() || path.charAt(0) != '/' || path.indexOf('%') >= 0 || path.indexOf('\\') >= 0
                || path.indexOf(';') >= 0 || path.contains("//")) {
            return null;
        }
        boolean unsafeSegment = Arrays.stream(path.split("/", -1))
                .anyMatch(segment -> ".".equals(segment) || "..".equals(segment));
        return unsafeSegment ? null : path;
    }

    private boolean matches(String path, String root) {
        return path.equals(root) || path.startsWith(root + "/");
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring("Bearer ".length()).trim();
        return token.isBlank() ? null : token;
    }

    private boolean allowedRoute(RouteKind routeKind, AuthIdentityDTO identity) {
        return switch (routeKind) {
            case ADMIN -> AuthSubjectTypes.ADMIN_PRIMARY.equals(identity.getSubjectType())
                    || AuthSubjectTypes.ADMIN_SUB_ACCOUNT.equals(identity.getSubjectType());
            case APP -> AuthSubjectTypes.CUSTOMER.equals(identity.getSubjectType());
            case EXTERNAL -> AuthSubjectTypes.PLATFORM_CLIENT.equals(identity.getSubjectType())
                    && Constants.TokenKind.CHANNEL_HMAC.equals(identity.getTokenKind());
            default -> false;
        };
    }

    private boolean validIdentity(AuthIdentityDTO identity) {
        if (identity == null || isBlank(identity.getSubjectType()) || isBlank(identity.getSubjectId())
                || isBlank(identity.getTokenKind())) {
            return false;
        }
        return switch (identity.getSubjectType()) {
            case AuthSubjectTypes.ADMIN_PRIMARY, AuthSubjectTypes.ADMIN_SUB_ACCOUNT ->
                    identity.getAccountId() != null && identity.getUserId() != null && !isBlank(identity.getSessionId())
                            && Constants.TokenKind.OPAQUE.equals(identity.getTokenKind());
            case AuthSubjectTypes.CUSTOMER -> identity.getUserId() != null && !isBlank(identity.getSessionId())
                    && Constants.TokenKind.OPAQUE.equals(identity.getTokenKind());
            case AuthSubjectTypes.PLATFORM_CLIENT ->
                    !isBlank(identity.getClientId()) && Constants.TokenKind.CHANNEL_HMAC.equals(identity.getTokenKind())
                    && identity.getOwnerAccountId() == null && identity.getCredentialVersion() != null
                            && Constants.AuthScope.EXTERNAL_ACCESS.equals(identity.getAuthorizedScope());
            default -> false;
        };
    }

    private String stringValue(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String validOrGenerate(String value) {
        String validated = ContextValidator.validOrNull(value);
        return validated == null ? UUID.randomUUID().toString() : validated;
    }

    private AppException authRequired(String info) {
        return new AppException(Constants.ResponseCode.AUTH_REQUIRED.getCode(), info);
    }

    private void resolveException(HttpServletRequest request, HttpServletResponse response,
                                  RuntimeException exception) {
        ModelAndView resolved = handlerExceptionResolver.resolveException(request, response, null, exception);
        if (resolved == null) {
            throw exception;
        }
    }

    private enum RouteKind {
        ANONYMOUS,
        PLATFORM,
        ADMIN,
        APP,
        EXTERNAL,
        ACTUATOR,
        DENIED
    }
}
