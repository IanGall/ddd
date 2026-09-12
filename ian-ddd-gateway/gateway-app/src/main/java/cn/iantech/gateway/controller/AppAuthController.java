package cn.iantech.gateway.controller;

import cn.iantech.api.model.auth.AuthSessionDTO;
import cn.iantech.api.model.auth.AuthSubjectTypes;
import cn.iantech.api.model.customer.CustomerLoginReq;
import cn.iantech.api.model.customer.CustomerUserDTO;
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
 * C 端注册、认证与自助会话入口。
 */
@RestController
@RequestMapping("/api/app/auth")
public class AppAuthController {

    private final GatewayAuthClient authClient;

    public AppAuthController(GatewayAuthClient authClient) {
        this.authClient = authClient;
    }

    @PostMapping("/register")
    public Response<CustomerUserDTO> register(@Valid @RequestBody AuthWebModels.RegisterRequest request) {
        return success(authClient.register(request.loginName(), request.password(), request.displayName()));
    }

    @PostMapping("/login")
    public Response<AuthWebModels.TokenResponse> login(
            @Valid @RequestBody AuthWebModels.AppLoginRequest request, HttpServletRequest servletRequest) {
        CustomerLoginReq login = new CustomerLoginReq();
        login.setLoginName(request.loginName());
        login.setPassword(request.password());
        login.setClientType(limited(request.clientType(), 32));
        login.setDeviceId(limited(request.deviceId(), 128));
        login.setIpAddress(limited(servletRequest.getRemoteAddr(), 64));
        login.setUserAgent(limited(servletRequest.getHeader("User-Agent"), 256));
        return success(toResponse(requireApp(authClient.customerLogin(login))));
    }

    @PostMapping("/refresh")
    public Response<AuthWebModels.TokenResponse> refresh(
            @Valid @RequestBody AuthWebModels.RefreshRequest request, HttpServletRequest servletRequest) {
        return success(toResponse(requireApp(authClient.refresh(
                refreshRequest(request, servletRequest, AuthSubjectTypes.CUSTOMER)))));
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
