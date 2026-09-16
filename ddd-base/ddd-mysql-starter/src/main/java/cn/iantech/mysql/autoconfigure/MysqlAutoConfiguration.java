package cn.iantech.mysql.autoconfigure;

import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.id.GlobalIdGeneratorProvider;
import cn.iantech.mysql.interceptor.IdAutoFillInterceptor;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis 主键自动填充自动装配。
 *
 * <p>只注册一个 {@link Interceptor} Bean，不碰数据源也不碰 {@code SqlSessionFactory}：
 * MyBatis 的 Spring Boot 自动装配会把容器里所有 {@code Interceptor} Bean 收集起来作为 plugin
 * 装进 {@code SqlSessionFactory}，因此这里无需（也不应）自行配置工厂，以免与 ShardingSphere
 * 的数据源装配冲突。
 *
 * <p>两种生成器模式都支持：注解指定了业务名时用 {@link GlobalIdGeneratorProvider}，
 * 未指定业务名时用单生成器模式下的 {@link GlobalIdGenerator}。两者都用
 * {@link ObjectProvider} 惰性获取，取不到时由拦截器抛出带上下文的异常。
 */
@AutoConfiguration
@ConditionalOnClass({Interceptor.class, SqlSessionFactory.class, GlobalIdGenerator.class})
@EnableConfigurationProperties(MysqlProperties.class)
@ConditionalOnProperty(prefix = "ddd.mysql", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MysqlAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdAutoFillInterceptor.class)
    public IdAutoFillInterceptor idAutoFillInterceptor(
            ObjectProvider<GlobalIdGeneratorProvider> businessGenerators,
            ObjectProvider<GlobalIdGenerator> singleGenerator) {
        return new IdAutoFillInterceptor(
                business -> resolveGenerator(business, businessGenerators, singleGenerator));
    }

    /**
     * @param business 注解上的业务名；空表示单生成器模式
     * @return 对应生成器，没有可用生成器时返回 null（由拦截器给出带上下文的错误）
     */
    private static GlobalIdGenerator resolveGenerator(String business,
                                                      ObjectProvider<GlobalIdGeneratorProvider> businessGenerators,
                                                      ObjectProvider<GlobalIdGenerator> singleGenerator) {
        if (business == null || business.isEmpty()) {
            return singleGenerator.getIfAvailable();
        }
        GlobalIdGeneratorProvider provider = businessGenerators.getIfAvailable();
        return provider == null ? null : provider.forBusiness(business);
    }
}
