package cn.iantech.gateway.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理端与 C 端认证入口共享的强类型 Web 模型。
 */
public final class AuthWebModels {

    private AuthWebModels() {
    }

    public record AdminLoginRequest(
            @NotBlank(message = "登录名不能为空") String loginName,
            @NotBlank(message = "密码不能为空")
            @Size(min = 8, max = 72, message = "密码长度必须为8到72位") String password,
            @Size(max = 32, message = "客户端类型长度不能超过32") String clientType,
            @Size(max = 128, message = "设备ID长度不能超过128") String deviceId) {
    }

    public record AppLoginRequest(
            @NotBlank(message = "登录账号不能为空") @Size(max = 64, message = "登录账号长度不能超过64") String loginName,
            @NotBlank(message = "密码不能为空")
            @Size(min = 8, max = 72, message = "密码长度必须为8到72位") String password,
            @Size(max = 32, message = "客户端类型长度不能超过32") String clientType,
            @Size(max = 128, message = "设备ID长度不能超过128") String deviceId) {
    }

    public record RefreshRequest(
            @NotBlank(message = "刷新令牌不能为空") @Size(max = 256, message = "刷新令牌长度不能超过256")
            String refreshToken,
            @Size(max = 32, message = "客户端类型长度不能超过32") String clientType,
            @Size(max = 128, message = "设备ID长度不能超过128") String deviceId) {
    }

    public record RegisterRequest(
            @NotBlank(message = "登录账号不能为空") @Size(max = 64, message = "登录账号长度不能超过64") String loginName,
            @NotBlank(message = "密码不能为空")
            @Size(min = 8, max = 72, message = "密码长度必须为8到72位") String password,
            @Size(max = 128, message = "昵称长度不能超过128") String displayName) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            long refreshExpiresIn,
            String sessionId,
            Long userId,
            Long accountId,
            String username,
            String userType) {
    }
}
