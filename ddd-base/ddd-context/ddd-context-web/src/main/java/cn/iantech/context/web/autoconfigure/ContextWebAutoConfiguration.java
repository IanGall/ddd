package cn.iantech.context.web.autoconfigure;

import cn.iantech.context.web.AuthenticationContextResolver;
import cn.iantech.context.web.ContextWebFilter;
import cn.iantech.context.web.ResolvedAuthenticationContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Web 上下文过滤器自动装配。过滤器在应用认证过滤器之后执行。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ContextWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthenticationContextResolver authenticationContextResolver() {
        // 默认解析为匿名上下文，需要可信身份的应用自行注入解析器。
        return ResolvedAuthenticationContext::empty;
    }

    @Bean
    @ConditionalOnMissingBean
    public ContextWebFilter contextWebFilter(AuthenticationContextResolver resolver) {
        return new ContextWebFilter(resolver);
    }

    @Bean
    public FilterRegistrationBean<ContextWebFilter> contextWebFilterRegistration(ContextWebFilter filter) {
        FilterRegistrationBean<ContextWebFilter> registration = new FilterRegistrationBean<>(filter);
        // 使用最低优先级，确保认证过滤器先执行后再建立上下文。
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
    }
}
