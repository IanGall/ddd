package cn.iantech.cases.model;

import cn.iantech.common.exception.AppException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActorTest {

    @Test
    void shouldAcceptTrustedGatewayActorWithLongIds() {
        Actor actor = new Actor("request-1", 9_223_372_036_854_775_000L,
                9_223_372_036_854_774_999L, "operator", "ADMIN_SUB_ACCOUNT", "gateway");

        assertEquals(9_223_372_036_854_775_000L, actor.accountId());
        assertEquals(9_223_372_036_854_774_999L, actor.userId());
    }

    @Test
    void shouldRejectIncompleteOrUntrustedActor() {
        assertThrows(AppException.class,
                () -> new Actor("", 1L, 1L, "operator", "ADMIN_PRIMARY", "gateway"));
        assertThrows(AppException.class,
                () -> new Actor("request-1", 0L, 1L, "operator", "ADMIN_PRIMARY", "gateway"));
        assertThrows(AppException.class,
                () -> new Actor("request-1", 1L, 1L, "operator", "ADMIN_PRIMARY", "external"));
    }
}
