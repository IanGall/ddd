package cn.iantech.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "dubbo.registry.address=N/A",
                "dubbo.registry.username=test-user",
                "dubbo.registry.password=test-password",
                "dubbo.registry.use-as-metadata-center=false",
                "dubbo.config-center.address=N/A",
                "dubbo.application.metadata-type=local",
                "dubbo.application.metadata-service-protocol=injvm",
                "dubbo.consumer.init=false"
        })
class ApplicationContextTest {

    // 验证 Spring Boot 4 应用上下文能够成功加载
    @Test
    void shouldLoadSpringBoot4ApplicationContext() {
    }
}
