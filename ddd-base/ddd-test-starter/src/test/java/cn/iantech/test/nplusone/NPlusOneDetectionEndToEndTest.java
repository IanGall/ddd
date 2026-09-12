package cn.iantech.test.nplusone;

import cn.iantech.test.autoconfigure.DddTestAutoConfiguration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * 端到端验证：注解 + 扩展 + 数据源代理能在真实执行中判定 N+1。
 *
 * <p>用 {@link EngineTestKit} 运行内嵌用例，因此「必须失败」的用例不会让本构建变红。</p>
 */
class NPlusOneDetectionEndToEndTest {

    @Test
    void shouldFailTestCaseRepeatingSameSelect() {
        EngineExecutionResults results = execute(RepeatedSelectCase.class);

        results.testEvents().assertStatistics(stats -> stats.started(1).failed(1).succeeded(0));
        assertThat(failureOf(results)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("检测到 N+1")
                .hasMessageContaining("重复 SELECT超过阈值");
    }

    @Test
    void shouldPassTestCaseWithinBudget() {
        EngineExecutionResults results = execute(CompliantCase.class);

        results.testEvents().assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    @Test
    void shouldFailTestCaseRepeatingSameRemoteOperation() {
        EngineExecutionResults results = execute(RepeatedRemoteCallCase.class);

        results.testEvents().assertStatistics(stats -> stats.started(1).failed(1).succeeded(0));
        assertThat(failureOf(results)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("重复远程调用超过阈值");
    }

    @Test
    void shouldPassTestCaseWithIgnoredSqlPattern() {
        EngineExecutionResults results = execute(IgnoredSqlCase.class);

        results.testEvents().assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    private static EngineExecutionResults execute(Class<?> testClass) {
        return EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(testClass))
                .execute();
    }

    private static Throwable failureOf(EngineExecutionResults results) {
        List<Event> failures = results.testEvents().failed().list();
        assertThat(failures).hasSize(1);
        return failures.getFirst().getRequiredPayload(TestExecutionResult.class)
                .getThrowable()
                .orElseThrow();
    }

    private static void executeSql(SqlRunner runner) throws SQLException {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        DataSource dataSource = (DataSource) DddTestAutoConfiguration.dddTestDataSourcePostProcessor()
                .postProcessAfterInitialization(h2, "dataSource");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create table sample (id int primary key, name varchar(20))");
            statement.execute("insert into sample values (1, 'a'), (2, 'b')");
            runner.run(statement);
        }
    }

    @FunctionalInterface
    interface SqlRunner {

        void run(Statement statement) throws SQLException;
    }

    static class RepeatedSelectCase {

        @Test
        @DetectNPlusOne
        void shouldBeFlaggedAsNPlusOne() throws SQLException {
            executeSql(statement -> {
                statement.executeQuery("select name from sample where id = 1").close();
                statement.executeQuery("select name from sample where id = 2").close();
            });
        }
    }

    static class CompliantCase {

        @Test
        @DetectNPlusOne(maxSelects = 2)
        void shouldStayWithinBudget() throws SQLException {
            executeSql(statement -> {
                statement.executeQuery("select name from sample where id = 1").close();
                statement.executeQuery("select id from sample where name = 'a'").close();
            });
        }
    }

    static class RepeatedRemoteCallCase {

        @Test
        @DetectNPlusOne
        void shouldBeFlaggedAsNPlusOne() {
            NPlusOneContext.recordRemote("cn.iantech.api.IAuthService#validate");
            NPlusOneContext.recordRemote("cn.iantech.api.IAuthService#validate");
        }
    }

    static class IgnoredSqlCase {

        @Test
        @DetectNPlusOne(maxRepeatedSelects = 1, ignoredSqlPatterns = "selectnamefromsample.*")
        void shouldIgnoreConfiguredSql() throws SQLException {
            executeSql(statement -> {
                statement.executeQuery("select name from sample where id = 1").close();
                statement.executeQuery("select name from sample where id = 2").close();
            });
        }
    }
}
