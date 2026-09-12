package cn.iantech.context.web;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Web 上下文过滤器自动装配。过滤器在 Sa-Token 认证过滤器之后执行。
 */
@AutoConfiguration
@ConditionalOnClass(StpUtil.class)
public class ContextWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthenticationContextResolver authenticationContextResolver() {
        return new DefaultAuthenticationContextResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public ContextWebFilter contextWebFilter(AuthenticationContextResolver resolver) {
        return new ContextWebFilter(resolver);
    }

    @Bean
    public FilterRegistrationBean<ContextWebFilter> contextWebFilterRegistration(ContextWebFilter filter) {
        FilterRegistrationBean<ContextWebFilter> registration = new FilterRegistrationBean<>(filter);
        // 使用最低优先级确保认证完成后再建立上下文。
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
    }
}
