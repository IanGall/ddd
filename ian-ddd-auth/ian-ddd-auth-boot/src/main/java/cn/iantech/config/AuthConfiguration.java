package cn.iantech.config;

import cn.iantech.cases.auth.model.AuthCaseModels.TokenPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auth 令牌生命周期装配。身份认证实现（{@code RbacIdentityAuthenticator} /
 * {@code CustomerIdentityAuthenticator}）以 {@code @Service} 参与组件扫描，不再手工装配。
 */
@Configuration
public class AuthConfiguration {

    @Bean
    public TokenPolicy authTokenPolicy(
            @Value("${auth.security.session.access-token-timeout:900}") long accessTokenTimeout,
            @Value("${auth.security.session.refresh-token-timeout:2592000}") long refreshTokenTimeout) {
        return new TokenPolicy(accessTokenTimeout, refreshTokenTimeout);
    }
}
