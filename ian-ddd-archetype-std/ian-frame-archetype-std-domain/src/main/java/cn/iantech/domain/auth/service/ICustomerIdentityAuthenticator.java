package cn.iantech.domain.auth.service;

import cn.iantech.domain.auth.model.AuthenticatedIdentity;

/**
 * C 端身份认证端口，由 Customer 域实现。
 */
public interface ICustomerIdentityAuthenticator {

    AuthenticatedIdentity authenticate(String loginName, String password);

    AuthenticatedIdentity reload(Long customerId);
}
