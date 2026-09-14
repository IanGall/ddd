package cn.iantech.gateway.core.service;

import cn.iantech.api.IAuthService;
import cn.iantech.api.ICustomerService;
import cn.iantech.api.model.auth.*;
import cn.iantech.api.model.channel.ChannelSignatureVerifyReq;
import cn.iantech.api.model.customer.CustomerLoginReq;
import cn.iantech.api.model.customer.CustomerRegisterReq;
import cn.iantech.api.model.customer.CustomerUserDTO;
import org.apache.dubbo.config.annotation.DubboReference;

import java.util.List;
import java.util.function.Supplier;

import static cn.iantech.common.constant.Constants.ResponseCode.AUTH_UNAVAILABLE;

/**
 * 网关到 Auth 服务的 RPC 适配器。网关不持有令牌或会话状态。
 *
 * <p>由 {@code GatewayCoreAutoConfiguration} 以 Bean 形式注册，不依赖组件扫描。</p>
 */
public class GatewayAuthClient {

    @DubboReference(version = "1.0.0", protocol = "tri", timeout = 3000, retries = 0, check = false)
    private IAuthService authService;

    @DubboReference(version = "1.0.0", protocol = "tri", timeout = 3000, retries = 0, check = false)
    private ICustomerService customerService;

    public CustomerUserDTO register(CustomerRegisterReq request) {
        return invoke(() -> customerService.register(request));
    }

    public AuthTokenDTO login(AuthLoginReq request) {
        return invoke(() -> authService.login(request));
    }

    public AuthTokenDTO customerLogin(CustomerLoginReq request) {
        return invoke(() -> authService.customerLogin(request));
    }

    public AuthIdentityDTO authenticateChannel(ChannelSignatureVerifyReq request) {
        return invoke(() -> authService.authenticateChannel(request));
    }

    public AuthTokenDTO refresh(AuthRefreshReq request) {
        return invoke(() -> authService.refresh(request));
    }

    public AuthIdentityDTO validate(String accessToken) {
        return invoke(() -> authService.validate(AuthValidateReq.builder().accessToken(accessToken).build()));
    }

    public void logout(String accessToken) {
        invoke(() -> authService.logout(AuthLogoutReq.builder().accessToken(accessToken).build()));
    }

    public void logoutAll(String accessToken) {
        invoke(() -> authService.logoutAll(AuthLogoutAllReq.builder().accessToken(accessToken).build()));
    }

    public List<AuthSessionDTO> sessions(String accessToken) {
        return invoke(() -> authService.sessions(AuthSessionQueryReq.builder().accessToken(accessToken).build()));
    }

    public void revokeSession(String accessToken, String sessionId) {
        invoke(() -> authService.revokeSession(AuthRevokeSessionReq.builder().accessToken(accessToken)
                .sessionId(sessionId).build()));
    }

    private <T> T invoke(Supplier<T> invocation) {
        return RpcCallGuard.call(invocation, AUTH_UNAVAILABLE);
    }

    private void invoke(Runnable invocation) {
        RpcCallGuard.call(invocation, AUTH_UNAVAILABLE);
    }
}
