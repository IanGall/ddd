package cn.iantech.gateway;

import cn.iantech.api.model.rbac.RbacUserDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 装配层验证：接入工程注入的 ObjectMapper 必须真的带上「标识符按字符串出网」的模块。
 *
 * <p>{@code IdentifierAsStringModuleTest} 用的是手工构造的 Mapper，只能证明模块本身正确，
 * 证明不了 gateway-core 的自动装配被接入工程采纳；这一段由本测试补齐。</p>
 */
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
class GatewayIdentifierAsStringWiringTest {

    private static final long SNOWFLAKE_ID = 869643386981388293L;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void autoConfiguredObjectMapperShouldSerializeIdentifiersAsStrings() {
        assertThat(objectMapper.writeValueAsString(
                RbacUserDTO.builder().id(SNOWFLAKE_ID).accountId(1L).username("u1").build()))
                .contains("\"id\":\"" + SNOWFLAKE_ID + "\"")
                .contains("\"accountId\":\"1\"");
    }
}
