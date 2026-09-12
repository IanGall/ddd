package cn.iantech.gateway.controller;

import cn.iantech.api.model.auth.AuthIdentityDTO;
import cn.iantech.api.model.auth.AuthRefreshReq;
import cn.iantech.api.model.auth.AuthSubjectTypes;
import cn.iantech.api.model.auth.AuthTokenDTO;
import cn.iantech.common.exception.AppException;
import cn.iantech.gateway.core.config.GatewayAuthFilter;
import cn.iantech.gateway.model.AuthWebModels;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

import static cn.iantech.common.constant.Constants.ResponseCode.AUTH_REQUIRED;

/**
 * 两类认证控制器共享的纯协议适配逻辑，不持有认证状态。
 */
final class AuthControllerSupport {

    private static final Set<String> ADMIN_SUBJECT_TYPES =
            Set.of(AuthSubjectTypes.ADMIN_PRIMARY, AuthSubjectTypes.ADMIN_SUB_ACCOUNT);

    private AuthControllerSupport() {
    }

    static String requiredAccessToken(HttpServletRequest request) {
        String token = GatewayAuthFilter.accessToken(request);
        if (token == null || token.isBlank()) {
            throw new AppException(AUTH_REQUIRED.getCode(), AUTH_REQUIRED.getInfo());
        }
        return token;
    }

    static String limited(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    static AuthRefreshReq refreshRequest(AuthWebModels.RefreshRequest request, HttpServletRequest servletRequest,
                                         String expectedSubjectType) {
        return AuthRefreshReq.builder()
                .refreshToken(request.refreshToken())
                .expectedSubjectType(expectedSubjectType)
                .clientType(limited(request.clientType(), 32))
                .deviceId(limited(request.deviceId(), 128))
                .ipAddress(limited(servletRequest.getRemoteAddr(), 64))
                .userAgent(limited(servletRequest.getHeader("User-Agent"), 256))
                .build();
    }

    static AuthWebModels.TokenResponse toResponse(AuthTokenDTO issued) {
        AuthIdentityDTO identity = issued.getIdentity();
        return new AuthWebModels.TokenResponse(issued.getAccessToken(), issued.getRefreshToken(), issued.getTokenType(),
                issued.getExpiresIn(), issued.getRefreshExpiresIn(), issued.getSessionId(), identity.getUserId(),
                identity.getAccountId(), identity.getUsername(), identity.getUserType());
    }

    static AuthTokenDTO requireAdmin(AuthTokenDTO issued) {
        return requireSubject(issued, ADMIN_SUBJECT_TYPES, "管理端认证身份无效");
    }

    static AuthTokenDTO requireApp(AuthTokenDTO issued) {
        return requireSubject(issued, Set.of(AuthSubjectTypes.CUSTOMER), "C 端认证身份无效");
    }

    private static AuthTokenDTO requireSubject(AuthTokenDTO issued, Set<String> expectedSubjectTypes, String message) {
        if (issued == null || issued.getIdentity() == null
                || !expectedSubjectTypes.contains(issued.getIdentity().getSubjectType())) {
            throw new AppException(AUTH_REQUIRED.getCode(), message);
        }
        return issued;
    }
}
