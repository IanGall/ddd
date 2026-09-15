package cn.iantech.gateway.core.config;

import cn.iantech.gateway.core.exception.GatewayExceptionHandler;
import cn.iantech.gateway.core.service.GatewayAuthClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 网关核心自动装配：认证过滤器、Auth RPC 适配器与统一异常处理。
 *
 * <p>以自动装配（而非组件扫描）注册，保证任意包名的接入工程（含 archetype 生成的工程）
 * 都能获得一致的网关行为。</p>
 */
@AutoConfiguration
public class GatewayCoreAutoConfiguration {

    @Bean
    public GatewayAuthClient gatewayAuthClient() {
        return new GatewayAuthClient();
    }

    @Bean
    public FilterRegistrationBean<GatewayAuthFilter> gatewayAuthFilterRegistration(
            GatewayAuthClient gatewayAuthClient,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        FilterRegistrationBean<GatewayAuthFilter> registration = new FilterRegistrationBean<>(
                new GatewayAuthFilter(gatewayAuthClient, handlerExceptionResolver));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        return registration;
    }

    @Bean
    public GatewayExceptionHandler gatewayExceptionHandler() {
        return new GatewayExceptionHandler();
    }

    /**
     * 把标识类字段按字符串出网，避免雪花 ID 超过 JavaScript 安全整数后在浏览器端被改写。
     *
     * <p>只改序列化方向，入参与控制器签名保持不变，详见 {@link IdentifierAsStringModule}。</p>
     */
    @Bean
    public JsonMapperBuilderCustomizer gatewayIdentifierAsStringCustomizer() {
        return builder -> builder.addModule(new IdentifierAsStringModule());
    }
}
