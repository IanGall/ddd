package cn.iantech.config;

import cn.iantech.cases.auth.model.AuthCaseModels.TokenPolicy;
import cn.iantech.domain.auth.infra.IPasswordEncoder;
import cn.iantech.domain.auth.service.IAdminIdentityAuthenticator;
import cn.iantech.domain.auth.service.ICustomerIdentityAuthenticator;
import cn.iantech.domain.customer.infra.ICustomerUserRepository;
import cn.iantech.domain.customer.service.CustomerIdentityAuthenticator;
import cn.iantech.domain.rbac.infra.IRbacAccountRepository;
import cn.iantech.domain.rbac.infra.IRbacAuthorizationRepository;
import cn.iantech.domain.rbac.infra.IRbacUserRepository;
import cn.iantech.domain.rbac.service.RbacIdentityAuthenticator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auth 令牌生命周期与身份认证装配。
 */
@Configuration
public class AuthConfiguration {

    @Bean
    public IAdminIdentityAuthenticator adminIdentityAuthenticator(IRbacAccountRepository accountRepository,
                                                                  IRbacUserRepository userRepository,
                                                                  IRbacAuthorizationRepository authorizationRepository,
                                                                  IPasswordEncoder passwordEncoder) {
        return new RbacIdentityAuthenticator(accountRepository, userRepository, authorizationRepository,
                passwordEncoder);
    }

    @Bean
    public ICustomerIdentityAuthenticator customerIdentityAuthenticator(
            ICustomerUserRepository customerUserRepository, IPasswordEncoder passwordEncoder) {
        return new CustomerIdentityAuthenticator(customerUserRepository, passwordEncoder);
    }

    @Bean
    public TokenPolicy authTokenPolicy(
            @Value("${auth.security.session.access-token-timeout:900}") long accessTokenTimeout,
            @Value("${auth.security.session.refresh-token-timeout:2592000}") long refreshTokenTimeout) {
        return new TokenPolicy(accessTokenTimeout, refreshTokenTimeout);
    }
}
