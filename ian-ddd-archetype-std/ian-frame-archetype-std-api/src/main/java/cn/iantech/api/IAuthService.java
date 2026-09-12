package cn.iantech.api;

import cn.iantech.api.model.auth.*;
import cn.iantech.api.model.channel.ChannelSignatureVerifyReq;
import cn.iantech.api.model.customer.CustomerLoginReq;
import cn.iantech.common.exception.AppException;

import java.util.List;

/**
 * 认证与会话服务契约，认证状态只由 Auth 服务持有。
 */
public interface IAuthService {

    AuthTokenDTO login(AuthLoginReq req) throws AppException;

    AuthTokenDTO customerLogin(CustomerLoginReq req) throws AppException;

    AuthIdentityDTO authenticateChannel(ChannelSignatureVerifyReq req) throws AppException;

    AuthTokenDTO refresh(AuthRefreshReq req) throws AppException;

    AuthIdentityDTO validate(AuthValidateReq req) throws AppException;

    void logout(AuthLogoutReq req) throws AppException;

    void logoutAll(AuthLogoutAllReq req) throws AppException;

    List<AuthSessionDTO> sessions(AuthSessionQueryReq req) throws AppException;

    void revokeSession(AuthRevokeSessionReq req) throws AppException;
}
