package cn.iantech.mysql.itest;

import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.id.GlobalIdGeneratorProvider;
import cn.iantech.mysql.autoconfigure.MysqlAutoConfiguration;
import cn.iantech.mysql.interceptor.IdAutoFillInterceptor;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用真实 MyBatis + H2 跑通「类上注解 → 拦截器填 id → 落库」的完整链路。
 *
 * <p>不依赖真实 MySQL，因此可以在本地与 CI 常态执行；拦截器位于 MyBatis 层，
 * 与具体 JDBC 驱动无关，H2 上验证过的行为对 ShardingSphere 驱动同样成立。
 */
class IdAutoFillMybatisIntegrationTest {

    private static final long FIRST_ID = 869643386981388293L;

    private final List<String> requestedBusinesses = new ArrayList<>();

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws SQLException {
        DataSource dataSource = newDataSource();
        sqlSessionFactory = buildFactory(dataSource);
        execute(dataSource, "CREATE TABLE sample_order (id BIGINT PRIMARY KEY, name VARCHAR(64) NOT NULL)");
        execute(dataSource, "CREATE TABLE plain_note (id BIGINT, body VARCHAR(64) NOT NULL)");
    }

    @Test
    void shouldFillIdBeforeInsertAndPersistIt() {
        SampleOrderPo po = new SampleOrderPo();
        po.setName("order-a");

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            session.getMapper(SampleOrderMapper.class).insert(po);
        }

        assertThat(po.getId()).isEqualTo(FIRST_ID);
        assertThat(requestedBusinesses).containsExactly("identity");
        try (SqlSession session = sqlSessionFactory.openSession()) {
            assertThat(session.getMapper(SampleOrderMapper.class).findNameById(FIRST_ID)).isEqualTo("order-a");
        }
    }

    @Test
    void shouldNotOverwriteIdProvidedByCaller() {
        SampleOrderPo po = new SampleOrderPo();
        po.setId(7L);
        po.setName("order-b");

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            session.getMapper(SampleOrderMapper.class).insert(po);
        }

        assertThat(po.getId()).isEqualTo(7L);
        assertThat(requestedBusinesses).as("已经有 id 时不应消耗号段").isEmpty();
        try (SqlSession session = sqlSessionFactory.openSession()) {
            assertThat(session.getMapper(SampleOrderMapper.class).findNameById(7L)).isEqualTo("order-b");
        }
    }

    @Test
    void shouldNotFillUnannotatedEntityEvenWhenSqlBindsId() {
        PlainNotePo note = new PlainNotePo();
        note.setBody("note-a");

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            session.getMapper(PlainNoteMapper.class).insert(note);
        }

        assertThat(note.getId()).as("没标注解的类一律不碰").isNull();
        assertThat(requestedBusinesses).isEmpty();
        try (SqlSession session = sqlSessionFactory.openSession()) {
            assertThat(session.getMapper(PlainNoteMapper.class).countByBody("note-a")).isEqualTo(1);
        }
    }

    /**
     * Spring 场景：只声明一个 {@code Interceptor} Bean 即可，MyBatis 的自动装配会把它装成插件；
     * 注解上的业务名也要能正确路由到 {@code GlobalIdGeneratorProvider.forBusiness}。
     */
    @Test
    void shouldInstallInterceptorAndRouteBusinessNameThroughSpring() {
        GlobalIdGeneratorProvider provider = new GlobalIdGeneratorProvider() {
            @Override
            public GlobalIdGenerator forBusiness(String business) {
                requestedBusinesses.add(business);
                return () -> FIRST_ID;
            }

            @Override
            public void close() {
            }
        };
        DataSource dataSource = newDataSource();

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MybatisAutoConfiguration.class,
                        MysqlAutoConfiguration.class))
                .withBean(DataSource.class, () -> dataSource)
                .withBean(GlobalIdGeneratorProvider.class, () -> provider)
                .withPropertyValues("mybatis.mapper-locations=classpath:mybatis-itest/*.xml")
                .run(context -> {
                    SqlSessionFactory factory = context.getBean(SqlSessionFactory.class);
                    assertThat(factory.getConfiguration().getInterceptors())
                            .as("容器里的 Interceptor Bean 应被 MyBatis 自动装配装成插件")
                            .hasSize(1)
                            .first()
                            .isInstanceOf(IdAutoFillInterceptor.class);

                    executeUnchecked(dataSource,
                            "CREATE TABLE sample_order (id BIGINT PRIMARY KEY, name VARCHAR(64) NOT NULL)");
                    SampleOrderPo po = new SampleOrderPo();
                    po.setName("order-spring");
                    context.getBean(SqlSessionTemplate.class)
                            .getMapper(SampleOrderMapper.class).insert(po);

                    assertThat(po.getId()).isEqualTo(FIRST_ID);
                    assertThat(requestedBusinesses).containsExactly("identity");
                });
    }

    private IdAutoFillInterceptor interceptor() {
        return new IdAutoFillInterceptor(business -> {
            requestedBusinesses.add(business);
            return () -> FIRST_ID;
        });
    }

    private SqlSessionFactory buildFactory(DataSource dataSource) {
        Configuration configuration = new Configuration();
        configuration.setEnvironment(new Environment("itest", new JdbcTransactionFactory(), dataSource));
        configuration.addInterceptor(interceptor());
        parseMapper(configuration, "mybatis-itest/sample-order-mapper.xml");
        parseMapper(configuration, "mybatis-itest/plain-note-mapper.xml");
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private void parseMapper(Configuration configuration, String resource) {
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        } catch (Exception exception) {
            throw new IllegalStateException("加载测试 mapper 失败：" + resource, exception);
        }
    }

    private DataSource newDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:idfill-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        return dataSource;
    }

    private void execute(DataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void executeUnchecked(DataSource dataSource, String sql) {
        try {
            execute(dataSource, sql);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
