package cn.iantech.trigger.rpc;

import cn.iantech.api.IAuthService;
import cn.iantech.api.model.auth.*;
import cn.iantech.api.model.channel.ChannelSignatureVerifyReq;
import cn.iantech.api.model.customer.CustomerLoginReq;
import cn.iantech.cases.auth.model.AuthCaseModels.LoginCommand;
import cn.iantech.cases.auth.model.AuthCaseModels.RefreshCommand;
import cn.iantech.cases.auth.service.AuthCaseService;
import cn.iantech.cases.channel.model.ChannelCaseModels.SignatureCommand;
import cn.iantech.cases.channel.service.ChannelAuthCaseService;
import cn.iantech.trigger.convertor.AuthApiConverter;
import cn.iantech.trigger.convertor.AuthCommandConvertor;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/**
 * Auth Dubbo 入站适配器，负责 API DTO 与认证用例模型的转换。
 */
@DubboService(version = "1.0.0", protocol = "tri", timeout = 3000)
@RequiredArgsConstructor
public class AuthService implements IAuthService {

    private final AuthCaseService authCaseService;
    private final ChannelAuthCaseService channelAuthCaseService;
    private final AuthCommandConvertor commandConvertor;

    @Override
    public AuthTokenDTO login(AuthLoginReq req) {
        LoginCommand command = commandConvertor.toCommand(req);
        return AuthApiConverter.toTokenDTO(authCaseService.login(command));
    }

    @Override
    public AuthTokenDTO customerLogin(CustomerLoginReq req) {
        LoginCommand command = commandConvertor.toCommand(req);
        return AuthApiConverter.toTokenDTO(authCaseService.customerLogin(command));
    }

    @Override
    public AuthIdentityDTO authenticateChannel(ChannelSignatureVerifyReq req) {
        SignatureCommand command = commandConvertor.toCommand(req);
        return AuthApiConverter.toIdentityDTO(channelAuthCaseService.authenticate(command));
    }

    @Override
    public AuthTokenDTO refresh(AuthRefreshReq req) {
        RefreshCommand command = commandConvertor.toCommand(req);
        return AuthApiConverter.toTokenDTO(authCaseService.refresh(command));
    }

    @Override
    public AuthIdentityDTO validate(AuthValidateReq req) {
        return AuthApiConverter.toIdentityDTO(authCaseService.validate(req == null ? null : req.getAccessToken()));
    }

    @Override
    public void logout(AuthLogoutReq req) {
        authCaseService.logout(req == null ? null : req.getAccessToken());
    }

    @Override
    public void logoutAll(AuthLogoutAllReq req) {
        authCaseService.logoutAll(req == null ? null : req.getAccessToken());
    }

    @Override
    public List<AuthSessionDTO> sessions(AuthSessionQueryReq req) {
        return authCaseService.sessions(req == null ? null : req.getAccessToken()).stream()
                .map(AuthApiConverter::toSessionDTO)
                .toList();
    }

    @Override
    public void revokeSession(AuthRevokeSessionReq req) {
        authCaseService.revokeSession(req == null ? null : req.getAccessToken(), req == null ? null : req.getSessionId());
    }
}
