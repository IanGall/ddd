package cn.iantech.gateway;

import cn.iantech.api.IAuthService;
import cn.iantech.api.model.auth.AuthIdentityDTO;
import cn.iantech.api.model.auth.AuthValidateReq;
import cn.iantech.test.nplusone.DetectNPlusOne;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 网关消费端远程调用的 N+1 防护：一次业务流程只允许发起一次下游 Dubbo 调用。
 *
 * <p>网关是 Dubbo 消费端，SQL 观测通道在这里不适用，「逐元素调用下游」只能靠消费端过滤器发现。
 * 用例在同一个 JVM 内导出 {@link IAuthService} 测试提供者并直连调用，不依赖 Nacos 与标准服务，
 * 因此可以随常规构建执行。</p>
 */
class GatewayRemoteNPlusOneTest {

    private static final String SERVICE_VERSION = "1.0.0";

    private static IAuthService providerRef;

    private static ServiceConfig<IAuthService> provider;

    private static ReferenceConfig<IAuthService> consumer;

    @BeforeAll
    static void startInProcessDubbo() throws IOException {
        int port = findFreePort();
        providerRef = Mockito.mock(IAuthService.class);
        when(providerRef.validate(any())).thenReturn(AuthIdentityDTO.builder()
                .accountId(1L)
                .userId(1L)
                .username("nplusone-consumer")
                .userType("PRIMARY")
                .subjectType("ADMIN_PRIMARY")
                .build());

        ApplicationConfig application = new ApplicationConfig("gateway-nplusone-test");
        application.setQosEnable(false);
        application.setMetadataType("local");

        provider = new ServiceConfig<>();
        provider.setApplication(application);
        provider.setRegistry(new RegistryConfig(RegistryConfig.NO_AVAILABLE));
        provider.setProtocol(new ProtocolConfig("dubbo", port));
        provider.setInterface(IAuthService.class);
        provider.setRef(providerRef);
        provider.setVersion(SERVICE_VERSION);
        provider.export();

        consumer = new ReferenceConfig<>();
        consumer.setApplication(application);
        consumer.setInterface(IAuthService.class);
        consumer.setVersion(SERVICE_VERSION);
        consumer.setUrl("dubbo://127.0.0.1:" + port);
        consumer.setCheck(false);
        consumer.setTimeout(3000);
    }

    @AfterAll
    static void stopInProcessDubbo() {
        if (consumer != null) {
            consumer.destroy();
        }
        if (provider != null) {
            provider.unexport();
        }
        // 原生 API 会注册 JVM 级默认模型，必须重置，避免后续 Spring Boot 测试上下文复用它而缺少注册中心
        DubboBootstrap.reset();
    }

    @Test
    @DetectNPlusOne(maxRemoteCalls = 1, maxRepeatedRemoteCalls = 1)
    void shouldIssueExactlyOneRemoteCallPerGatewayInvocation() {
        AuthIdentityDTO identity = consumer.get().validate(AuthValidateReq.builder().accessToken("token-1").build());

        assertThat(identity.getUserId()).isEqualTo(1L);
        verify(providerRef, times(1)).validate(any());
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
