package cn.iantech.context.web;

import cn.iantech.context.core.ContextAccessor;
import cn.iantech.context.core.ContextScope;
import cn.iantech.context.core.ContextValidator;
import cn.iantech.context.core.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/**
 * 在应用认证过滤器之后建立协议无关的请求上下文，默认解析出匿名身份。
 */
public final class ContextWebFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final AuthenticationContextResolver authenticationContextResolver;

    public ContextWebFilter() {
        this(ResolvedAuthenticationContext::empty);
    }

    public ContextWebFilter(AuthenticationContextResolver authenticationContextResolver) {
        this.authenticationContextResolver = Objects.requireNonNull(
                authenticationContextResolver, "认证上下文解析器不能为空");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = validOrGenerate(request.getHeader(REQUEST_ID_HEADER));
        ResolvedAuthenticationContext resolved = authenticationContextResolver.resolve();
        if (resolved == null) {
            resolved = ResolvedAuthenticationContext.empty();
        }

        RequestContext context = new RequestContext(
                requestId,
                resolved.principalName(),
                resolved.tenantId(),
                resolved.userId(),
                resolved.subjectType(),
                resolved.clientId(),
                null,
                RequestContext.SOURCE_GATEWAY,
                null,
                resolved.ownerAccountId(),
                resolved.authorizedScope(),
                resolved.credentialVersion());

        try (ContextScope ignored = ContextAccessor.open(context)) {
            response.setHeader(REQUEST_ID_HEADER, requestId);
            filterChain.doFilter(request, response);
        }
    }

    private String validOrGenerate(String value) {
        String validated = ContextValidator.validOrNull(value);
        return validated == null ? UUID.randomUUID().toString() : validated;
    }
}
