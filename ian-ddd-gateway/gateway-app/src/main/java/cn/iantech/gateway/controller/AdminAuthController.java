package cn.iantech.gateway.controller;

import cn.iantech.api.model.auth.AuthLoginReq;
import cn.iantech.api.model.auth.AuthSessionDTO;
import cn.iantech.api.model.auth.AuthSubjectTypes;
import cn.iantech.common.model.Response;
import cn.iantech.gateway.model.AuthWebModels;
import cn.iantech.gateway.service.GatewayAuthClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iantech.gateway.controller.AuthControllerSupport.*;
import static cn.iantech.gateway.model.GatewayResponses.success;

/**
 * 管理主体认证与自助会话入口。
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final GatewayAuthClient authClient;

    public AdminAuthController(GatewayAuthClient authClient) {
        this.authClient = authClient;
    }

    @PostMapping("/login")
    public Response<AuthWebModels.TokenResponse> login(
            @Valid @RequestBody AuthWebModels.AdminLoginRequest request, HttpServletRequest servletRequest) {
        return success(toResponse(requireAdmin(authClient.login(AuthLoginReq.builder()
                .loginName(request.loginName())
                .password(request.password())
                .clientType(limited(request.clientType(), 32))
                .deviceId(limited(request.deviceId(), 128))
                .ipAddress(limited(servletRequest.getRemoteAddr(), 64))
                .userAgent(limited(servletRequest.getHeader("User-Agent"), 256))
                .build()))));
    }

    @PostMapping("/refresh")
    public Response<AuthWebModels.TokenResponse> refresh(
            @Valid @RequestBody AuthWebModels.RefreshRequest request, HttpServletRequest servletRequest) {
        return success(toResponse(requireAdmin(authClient.refresh(
                refreshRequest(request, servletRequest, AuthSubjectTypes.ADMIN)))));
    }

    @PostMapping("/logout")
    public Response<Void> logout(HttpServletRequest request) {
        authClient.logout(requiredAccessToken(request));
        return success(null);
    }

    @PostMapping("/logout-all")
    public Response<Void> logoutAll(HttpServletRequest request) {
        authClient.logoutAll(requiredAccessToken(request));
        return success(null);
    }

    @GetMapping("/sessions")
    public Response<List<AuthSessionDTO>> sessions(HttpServletRequest request) {
        return success(authClient.sessions(requiredAccessToken(request)));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Response<Void> revokeSession(
            @Size(max = 64, message = "会话ID长度不能超过64") @PathVariable String sessionId,
            HttpServletRequest request) {
        authClient.revokeSession(requiredAccessToken(request), sessionId);
        return success(null);
    }
}
