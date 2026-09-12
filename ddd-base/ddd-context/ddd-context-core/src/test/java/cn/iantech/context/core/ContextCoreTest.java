package cn.iantech.context.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ContextCoreTest {

    // 验证嵌套作用域关闭后恢复父上下文，并在最外层关闭后完成清理
    @Test
    void shouldRestoreNestedScopeAndClearAfterClose() {
        RequestContext outer = new RequestContext("r1", "admin", null, null, null, null, null, "gateway", null,
                null, null, null);
        RequestContext inner = new RequestContext("r2", "service", null, null, null, null, "gray-a", "dubbo", "zh-CN",
                null, null, null);

        try (ContextScope ignored = ContextAccessor.open(outer)) {
            assertEquals(outer, ContextAccessor.current().orElseThrow());
            try (ContextScope nested = ContextAccessor.open(inner)) {
                assertEquals(inner, ContextAccessor.current().orElseThrow());
            }
            assertEquals(outer, ContextAccessor.current().orElseThrow());
        }
        assertFalse(ContextAccessor.current().isPresent());
    }

    // 验证上下文快照可传播任务上下文，并恢复执行线程原有上下文
    @Test
    void shouldPropagateContextWithSnapshotAndRestoreWorkerThreadContext() {
        RequestContext parent = new RequestContext("r1", "admin", null, null, null, null, null, "gateway", null,
                null, null, null);
        RequestContext worker = new RequestContext("r2", "worker", null, null, null, null, null, "worker", null,
                null, null, null);
        AtomicReference<RequestContext> captured = new AtomicReference<>();

        try (ContextScope ignored = ContextAccessor.open(parent)) {
            ContextSnapshot snapshot = ContextSnapshot.capture();
            try (ContextScope workerScope = ContextAccessor.open(worker)) {
                snapshot.wrap(() -> captured.set(ContextAccessor.current().orElseThrow())).run();
                assertEquals(worker, ContextAccessor.current().orElseThrow());
            }
        }
        assertEquals(parent, captured.get());
        assertFalse(ContextAccessor.current().isPresent());
    }

    // 验证非法字段不会进入请求上下文
    @Test
    void shouldIgnoreInvalidFieldsAndExcludeThemFromContext() {
        RequestContext context = new RequestContext("bad value", "admin", null, null, null, null, null, "gateway", "中文",
                null, null, null);
        assertNull(context.requestId());
        assertNull(context.locale());
    }

    // 验证渠道认证上下文字段会被规范化，非法值不会进入可信上下文
    @Test
    void shouldNormalizeChannelCredentialContextFields() {
        RequestContext context = new RequestContext(
                "request-1", null, null, null, "CLIENT", "channel-a",
                null, "gateway", null, " 9223372036854775807 ", "external:access", " 12 ");

        assertEquals("9223372036854775807", context.ownerAccountId());
        assertEquals("external:access", context.authorizedScope());
        assertEquals("12", context.credentialVersion());

        RequestContext invalid = new RequestContext(
                "request-2", null, null, null, "CLIENT", "channel-a",
                null, "gateway", null, "owner account", "integration access", "version\n2");
        assertNull(invalid.ownerAccountId());
        assertNull(invalid.authorizedScope());
        assertNull(invalid.credentialVersion());
    }
}
