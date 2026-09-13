package cn.iantech.api.external.support;

import cn.iantech.api.external.sample.SampleExternalApi;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.annotation.HttpExchange;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外部 API 骨架约定：契约接口必须声明 {@code @HttpExchange}，且工厂能为其生成代理。
 */
class ExternalApiClientFactoryTest {

    @Test
    void contractInterfaceMustDeclareHttpExchange() {
        assertTrue(SampleExternalApi.class.isAnnotationPresent(HttpExchange.class),
                "外部契约接口必须标注 @HttpExchange");
    }

    @Test
    void shouldCreateProxyForContractInterface() {
        SampleExternalApi api = ExternalApiClientFactory.create(SampleExternalApi.class, "http://127.0.0.1:1");

        assertNotNull(api);
        assertTrue(SampleExternalApi.class.isAssignableFrom(api.getClass()));
    }

    @Test
    void shouldCreateProxyFromCustomRestClient() {
        SampleExternalApi api = ExternalApiClientFactory.create(SampleExternalApi.class,
                RestClient.builder().baseUrl("http://127.0.0.1:1").build());

        assertNotNull(api);
    }

    @Test
    void shouldRejectNullArguments() {
        assertThrows(NullPointerException.class,
                () -> ExternalApiClientFactory.create(null, "http://127.0.0.1:1"));
        assertThrows(NullPointerException.class,
                () -> ExternalApiClientFactory.create(SampleExternalApi.class, (String) null));
    }
}
