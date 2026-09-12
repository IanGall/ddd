package cn.iantech.domain.auth.service;

import cn.iantech.domain.auth.model.AuthenticatedIdentity;

/**
 * 管理端身份认证端口，由 RBAC 域实现。
 */
public interface IAdminIdentityAuthenticator {

    AuthenticatedIdentity authenticate(String loginName, String password);

    AuthenticatedIdentity reload(Long accountId, Long userId, String userType);
}
