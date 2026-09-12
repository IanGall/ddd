package cn.iantech.test.autoconfigure;

import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DddTestAutoConfigurationTest {

    @Test
    void shouldWrapDataSourceBeanWithProxyDataSource() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DddTestAutoConfiguration.class))
                .withBean(DataSource.class, DddTestAutoConfigurationTest::h2DataSource)
                .run(context -> {
                    assertThat(context).hasSingleBean(DataSource.class);
                    assertThat(context.getBean(DataSource.class)).isInstanceOf(ProxyDataSource.class);
                });
    }

    @Test
    void shouldBeRegisteredForSpringBootAutoConfiguration() throws IOException {
        try (InputStream in = getClass().getResourceAsStream(
                "/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(in).isNotNull();
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .contains(DddTestAutoConfiguration.class.getName());
        }
    }

    @Test
    void shouldKeepNonDataSourceAndAlreadyProxiedBeans() {
        BeanPostProcessor postProcessor = DddTestAutoConfiguration.dddTestDataSourcePostProcessor();
        Object plainBean = new Object();
        assertThat(postProcessor.postProcessAfterInitialization(plainBean, "plainBean")).isSameAs(plainBean);

        DataSource proxied = ProxyDataSourceBuilder.create(h2DataSource()).name("existing").build();
        assertThat(postProcessor.postProcessAfterInitialization(proxied, "proxied")).isSameAs(proxied);

        assertThat(postProcessor.postProcessAfterInitialization(h2DataSource(), "dataSource"))
                .isInstanceOf(ProxyDataSource.class);
    }

    private static DataSource h2DataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:ddd_test_autoconfigure;DB_CLOSE_DELAY=-1");
        return dataSource;
    }
}
