package cn.iantech.trigger.context;

import cn.iantech.cases.model.Actor;
import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.context.core.ContextAccessor;
import cn.iantech.context.core.ContextScope;
import cn.iantech.context.core.RequestContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 入口层可信上下文到显式操作者的转换契约。
 */
class ActorResolverTest {

    private final ActorResolver resolver = new ActorResolver();

    private static RequestContext context(String tenantId, String userId, String principalName, String source) {
        return new RequestContext("req-1", principalName, tenantId, userId, "ADMIN", "web",
                null, source, null, null, null, null);
    }

    @Test
    void shouldResolveActorFromTrustedGatewayContext() {
        try (ContextScope ignored = ContextAccessor.open(context("1001", "2002", "alice", "gateway"))) {
            Actor actor = resolver.resolve();

            assertEquals("req-1", actor.requestId());
            assertEquals(1001L, actor.accountId());
            assertEquals(2002L, actor.userId());
            assertEquals("alice", actor.principalName());
            assertEquals("ADMIN", actor.subjectType());
            assertEquals("gateway", actor.source());
        }
    }

    @Test
    void shouldDenyWhenContextMissing() {
        AppException exception = assertThrows(AppException.class, resolver::resolve);
        assertEquals(Constants.ResponseCode.ACCESS_DENIED.getCode(), exception.getCode());
    }

    @Test
    void shouldDenyWhenTenantIdNotPositiveNumber() {
        try (ContextScope ignored = ContextAccessor.open(context("not-a-number", "2002", "alice", "gateway"))) {
            AppException exception = assertThrows(AppException.class, resolver::resolve);
            assertEquals(Constants.ResponseCode.ACCESS_DENIED.getCode(), exception.getCode());
        }
    }

    @Test
    void shouldDenyWhenTenantIdIsZero() {
        try (ContextScope ignored = ContextAccessor.open(context("0", "2002", "alice", "gateway"))) {
            assertThrows(AppException.class, resolver::resolve);
        }
    }

    @Test
    void shouldDenyWhenSourceIsNotGateway() {
        try (ContextScope ignored = ContextAccessor.open(context("1001", "2002", "alice", "internal"))) {
            assertThrows(AppException.class, resolver::resolve);
        }
    }

    @Test
    void shouldRestorePreviousContextAfterScopeCloses() {
        try (ContextScope outer = ContextAccessor.open(context("1001", "2002", "alice", "gateway"))) {
            try (ContextScope ignored = ContextAccessor.open(context("3003", "4004", "bob", "gateway"))) {
                assertEquals(3003L, resolver.resolve().accountId());
            }
            assertEquals(1001L, resolver.resolve().accountId());
        }
        assertTrue(ContextAccessor.current().isEmpty());
    }
}
