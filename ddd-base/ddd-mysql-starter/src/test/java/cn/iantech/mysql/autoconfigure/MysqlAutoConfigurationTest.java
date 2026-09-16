package cn.iantech.mysql.autoconfigure;

import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.mysql.interceptor.IdAutoFillInterceptor;
import org.apache.ibatis.plugin.Interceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MysqlAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MysqlAutoConfiguration.class));

    @Test
    void shouldRegisterInterceptorAsMybatisPlugin() {
        contextRunner.withBean(GlobalIdGenerator.class, () -> () -> 1L)
                .run(context -> {
                    assertThat(context).hasSingleBean(IdAutoFillInterceptor.class);
                    // MyBatis 的 Spring Boot 自动装配正是按 Interceptor 类型收集插件的
                    assertThat(context.getBean(IdAutoFillInterceptor.class)).isInstanceOf(Interceptor.class);
                });
    }

    @Test
    void shouldRegisterEvenWithoutAnyGeneratorBean() {
        // 生成器缺失不应阻止注册：错误要等到真的插入时才暴露，并且带上实体类与业务名
        contextRunner.run(context -> assertThat(context).hasSingleBean(IdAutoFillInterceptor.class));
    }

    @Test
    void shouldNotRegisterWhenDisabled() {
        contextRunner.withBean(GlobalIdGenerator.class, () -> () -> 1L)
                .withPropertyValues("ddd.mysql.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(IdAutoFillInterceptor.class));
    }

    @Test
    void shouldKeepUserProvidedInterceptor() {
        IdAutoFillInterceptor custom = new IdAutoFillInterceptor(business -> () -> 7L);

        contextRunner.withBean(IdAutoFillInterceptor.class, () -> custom)
                .withBean(GlobalIdGenerator.class, () -> () -> 1L)
                .run(context -> assertThat(context).hasSingleBean(IdAutoFillInterceptor.class)
                        .getBean(IdAutoFillInterceptor.class).isSameAs(custom));
    }

    @Test
    void shouldRegisterAutoConfigurationImports() throws Exception {
        String resourceName = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

        try (var input = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            assertThat(input).isNotNull();
            assertThat(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .contains("cn.iantech.mysql.autoconfigure.MysqlAutoConfiguration");
        }
    }
}
