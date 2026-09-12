package cn.iantech.test.nplusone;

import cn.iantech.test.autoconfigure.DddTestAutoConfiguration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 数据源观测链路：自动装配把数据源包装为代理后，真实执行的 SQL 能被 N+1 观测捕获。
 */
class DataSourceObservationTest {

    @Test
    void shouldObserveRepeatedSelectsThroughProxiedDataSource() throws SQLException {
        DataSource dataSource = (DataSource) DddTestAutoConfiguration.dddTestDataSourcePostProcessor()
                .postProcessAfterInitialization(h2DataSource(), "dataSource");
        ObservationSession session = new ObservationSession(DefaultBudget.class.getAnnotation(DetectNPlusOne.class));

        NPlusOneContext.start(session);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create table sample (id int primary key, name varchar(20))");
            statement.execute("insert into sample values (1, 'a'), (2, 'b')");
            statement.executeQuery("select name from sample where id = 1").close();
            statement.executeQuery("select name from sample where id = 2").close();
        } finally {
            NPlusOneContext.clear(session);
        }

        assertThat(session.summary())
                .contains("selects=2")
                .contains("selectnamefromsamplewhereid=? × 2");
        assertThatThrownBy(session::assertWithinLimits)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("重复 SELECT超过阈值，actual=2, expected=1");
    }

    private static DataSource h2DataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:ddd_test_observation;DB_CLOSE_DELAY=-1");
        return dataSource;
    }

    @DetectNPlusOne
    static class DefaultBudget {
    }
}
