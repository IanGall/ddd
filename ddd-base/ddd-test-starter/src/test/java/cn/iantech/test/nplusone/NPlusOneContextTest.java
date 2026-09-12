package cn.iantech.test.nplusone;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NPlusOneContextTest {

    @Test
    void shouldRecordOnlyWhileSessionActive() {
        ObservationSession session = newSession();

        NPlusOneContext.recordSelect("SELECT 1");
        NPlusOneContext.start(session);
        try {
            NPlusOneContext.recordSelect("SELECT 1");
            NPlusOneContext.recordRemote("cn.iantech.api.IAuthService#validate");
        } finally {
            NPlusOneContext.clear(session);
        }

        assertThat(session.summary())
                .contains("selects=1")
                .contains("remoteCalls=1");

        NPlusOneContext.recordSelect("SELECT 2");
        assertThat(session.summary()).contains("selects=1");
    }

    @Test
    void shouldRejectConcurrentSessions() {
        ObservationSession first = newSession();
        ObservationSession second = newSession();

        NPlusOneContext.start(first);
        try {
            assertThatThrownBy(() -> NPlusOneContext.start(second))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("同一 JVM 不允许并行执行多个 N+1 检测测试");
        } finally {
            NPlusOneContext.clear(first);
        }

        NPlusOneContext.start(second);
        NPlusOneContext.clear(second);
    }

    @Test
    void shouldKeepSessionWhenClearingForeignSession() {
        ObservationSession active = newSession();
        ObservationSession foreign = newSession();

        NPlusOneContext.start(active);
        try {
            NPlusOneContext.clear(foreign);
            NPlusOneContext.recordSelect("SELECT 1");
        } finally {
            NPlusOneContext.clear(active);
        }

        assertThat(active.summary()).contains("selects=1");
    }

    private static ObservationSession newSession() {
        return new ObservationSession(DefaultBudget.class.getAnnotation(DetectNPlusOne.class));
    }

    @DetectNPlusOne
    static class DefaultBudget {
    }
}
