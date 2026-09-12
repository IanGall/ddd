package cn.iantech.test.nplusone;

import net.ttddyy.dsproxy.QueryInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatasourceProxyQueryListenerTest {

    private final DatasourceProxyQueryListener listener = new DatasourceProxyQueryListener();

    @Test
    void shouldCountSelectAndWithStatementsOnly() {
        ObservationSession session = startSession();
        try {
            listener.afterQuery(null, List.of(
                    query("  SELECT * FROM rbac_user WHERE id = 1"),
                    query("with latest as (select id from rbac_role) select * from latest"),
                    query("UPDATE rbac_user SET status = 0 WHERE id = 1"),
                    query("INSERT INTO rbac_user (id) VALUES (1)"),
                    query(null)));
        } finally {
            NPlusOneContext.clear(session);
        }

        assertThat(session.summary())
                .contains("selects=2")
                .contains("SELECT*FROMrbac_userWHEREid=? × 1")
                .doesNotContain("UPDATE")
                .doesNotContain("INSERT");
    }

    @Test
    void shouldIgnoreQueriesWithoutActiveSession() {
        assertThatCode(() -> listener.afterQuery(null, List.of(query("SELECT 1 FROM dual"))))
                .doesNotThrowAnyException();
    }

    private static ObservationSession startSession() {
        ObservationSession session = new ObservationSession(DefaultBudget.class.getAnnotation(DetectNPlusOne.class));
        NPlusOneContext.start(session);
        return session;
    }

    private static QueryInfo query(String sql) {
        QueryInfo queryInfo = mock(QueryInfo.class);
        when(queryInfo.getQuery()).thenReturn(sql);
        return queryInfo;
    }

    @DetectNPlusOne
    static class DefaultBudget {
    }
}
