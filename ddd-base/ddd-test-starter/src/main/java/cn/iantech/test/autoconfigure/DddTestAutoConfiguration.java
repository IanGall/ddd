package cn.iantech.test.autoconfigure;

import cn.iantech.test.nplusone.DatasourceProxyQueryListener;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * 测试组件自动装配：把容器内的数据源包装为 {@code datasource-proxy} 代理，使 SQL 可被观测。
 *
 * <p>组件只用于测试作用域，包装后的数据源与原数据源行为一致，仅额外记录执行过的语句。</p>
 */
@AutoConfiguration
public class DddTestAutoConfiguration {

    @Bean
    public static BeanPostProcessor dddTestDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!(bean instanceof DataSource dataSource) || bean instanceof ProxyDataSource) {
                    return bean;
                }
                return ProxyDataSourceBuilder.create(dataSource)
                        .name("ddd-test-" + beanName)
                        .listener(new DatasourceProxyQueryListener())
                        .build();
            }
        };
    }
}
