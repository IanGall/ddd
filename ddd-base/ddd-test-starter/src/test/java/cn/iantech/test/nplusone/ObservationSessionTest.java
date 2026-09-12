package cn.iantech.test.nplusone;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservationSessionTest {

    @Test
    void shouldCountSelectsAndGroupByNormalizedSql() {
        ObservationSession session = session(DefaultBudget.class);

        session.recordSelect("SELECT id FROM rbac_user WHERE id = 1");
        session.recordSelect("  SELECT   id  FROM rbac_user WHERE id = 2  ");

        assertThat(session.summary())
                .contains("selects=2")
                .contains("remoteCalls=0")
                .contains("SELECTidFROMrbac_userWHEREid=? × 2");
    }

    @Test
    void shouldNormalizeNullSql() {
        ObservationSession session = session(DefaultBudget.class);

        session.recordSelect(null);

        assertThat(session.summary()).contains("<null> × 1");
    }

    @Test
    void shouldIgnoreSqlMatchingConfiguredPatterns() {
        ObservationSession session = session(IgnoreAccountSql.class);

        session.recordSelect("SELECT * FROM rbac_account WHERE id = 1");
        session.recordSelect("SELECT * FROM rbac_user WHERE id = 1");

        assertThat(session.summary())
                .contains("selects=1")
                .doesNotContain("rbac_account");
    }

    @Test
    void shouldPassWhenWithinBudget() {
        ObservationSession session = session(DefaultBudget.class);

        session.recordSelect("SELECT id FROM rbac_user WHERE id = 1");

        assertThatCode(session::assertWithinLimits).doesNotThrowAnyException();
    }

    @Test
    void shouldFailWhenSelectTotalExceedsBudget() {
        ObservationSession session = session(TotalSelectBudget.class);

        session.recordSelect("SELECT id FROM rbac_user WHERE id = 1");
        session.recordSelect("SELECT id FROM rbac_role WHERE id = 1");

        assertThatThrownBy(session::assertWithinLimits)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("检测到 N+1")
                .hasMessageContaining("SELECT 总数超过阈值，actual=2, expected=1, operation=全部 SELECT");
    }

    @Test
    void shouldFailWhenSameSelectRepeats() {
        ObservationSession session = session(DefaultBudget.class);

        session.recordSelect("SELECT id FROM rbac_user WHERE id = 1");
        session.recordSelect("SELECT id FROM rbac_user WHERE id = 2");

        assertThatThrownBy(session::assertWithinLimits)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("重复 SELECT超过阈值，actual=2, expected=1, operation=SELECTidFROMrbac_userWHEREid=?");
    }

    @Test
    void shouldFailWhenRemoteCallsExceedBudget() {
        ObservationSession session = session(RemoteBudget.class);

        session.recordRemote("cn.iantech.api.IAuthService#validate");
        session.recordRemote("cn.iantech.api.IAuthService#refresh");

        assertThatThrownBy(session::assertWithinLimits)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("远程调用总数超过阈值，actual=2, expected=1, operation=全部远程调用");
    }

    @Test
    void shouldFailWhenSameRemoteOperationRepeats() {
        ObservationSession session = session(DefaultBudget.class);

        session.recordRemote("cn.iantech.api.IAuthService#validate");
        session.recordRemote("cn.iantech.api.IAuthService#validate");

        assertThatThrownBy(session::assertWithinLimits)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(
                        "重复远程调用超过阈值，actual=2, expected=1, operation=cn.iantech.api.IAuthService#validate");
    }

    @Test
    void shouldIgnoreConfiguredRemoteOperations() {
        ObservationSession session = session(IgnoreAuthRemote.class);

        session.recordRemote("cn.iantech.api.IAuthService#validate");
        session.recordRemote("cn.iantech.api.IRbacService#queryUserById");

        assertThat(session.summary())
                .contains("remoteCalls=1")
                .contains("cn.iantech.api.IRbacService#queryUserById × 1")
                .doesNotContain("IAuthService");
    }

    private static ObservationSession session(Class<?> annotatedType) {
        return new ObservationSession(annotatedType.getAnnotation(DetectNPlusOne.class));
    }

    @DetectNPlusOne
    static class DefaultBudget {
    }

    @DetectNPlusOne(maxSelects = 1)
    static class TotalSelectBudget {
    }

    @DetectNPlusOne(maxRemoteCalls = 1)
    static class RemoteBudget {
    }

    @DetectNPlusOne(ignoredSqlPatterns = ".*rbac_account.*")
    static class IgnoreAccountSql {
    }

    @DetectNPlusOne(ignoredRemoteOperations = "cn.iantech.api.IAuthService#validate")
    static class IgnoreAuthRemote {
    }
}
