package cn.iantech.api.external.support;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.util.Objects;

/**
 * 为外部 HTTP 契约接口生成客户端代理。
 *
 * <p>只使用 Spring Framework 自带能力（{@code RestClient} + {@code HttpServiceProxyFactory}），
 * 不依赖 Spring Cloud OpenFeign。调用方传入接口类型与目标基础地址即可：</p>
 *
 * <pre>{@code
 * SampleExternalApi api = ExternalApiClientFactory.create(SampleExternalApi.class, "https://partner.example.com");
 * SampleResponse response = api.getById("42");
 * }</pre>
 *
 * <p>如需超时、拦截器、认证等定制，可在调用方自行构建 {@code RestClient} 后使用
 * {@link #create(Class, RestClient)} 重载。</p>
 */
public final class ExternalApiClientFactory {

    private ExternalApiClientFactory() {
    }

    /**
     * 以基础地址创建代理。
     *
     * @param apiType 标注 {@code @HttpExchange} 的接口类型
     * @param baseUrl 目标服务基础地址，如 {@code https://partner.example.com}
     */
    public static <T> T create(Class<T> apiType, String baseUrl) {
        Objects.requireNonNull(apiType, "apiType 不能为空");
        Objects.requireNonNull(baseUrl, "baseUrl 不能为空");
        return create(apiType, RestClient.builder().baseUrl(baseUrl).build());
    }

    /**
     * 以调用方自定义的 {@link RestClient} 创建代理（用于配置超时、拦截器、认证等）。
     */
    public static <T> T create(Class<T> apiType, RestClient restClient) {
        Objects.requireNonNull(apiType, "apiType 不能为空");
        Objects.requireNonNull(restClient, "restClient 不能为空");
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(apiType);
    }
}
