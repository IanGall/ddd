package cn.iantech.context.dubbo;

import cn.iantech.context.core.ContextAccessor;
import cn.iantech.context.core.ContextKeys;
import cn.iantech.context.core.ContextScope;
import cn.iantech.context.core.RequestContext;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class DubboContextFilterTest {

    private final Invocation invocation = new RpcInvocation(
            "test", "cn.iantech.TestService", "1.0.0", new Class<?>[0], new Object[0]);

    // 每个测试结束后清理 Dubbo 上下文，避免测试之间相互污染
    @AfterEach
    void clearDubboContext() {
        RpcContext.removeClientAttachment();
        RpcContext.removeServerAttachment();
    }

    // 验证 Dubbo SPI 能够加载消费端和提供端上下文过滤器
    @Test
    void shouldLoadConsumerAndProviderFiltersThroughDubboSpi() {
        Filter consumer = ExtensionLoader.getExtensionLoader(Filter.class)
                .getExtension("dubboContextConsumer");
        Filter provider = ExtensionLoader.getExtensionLoader(Filter.class)
                .getExtension("dubboContextProvider");

        assertInstanceOf(DubboContextConsumerFilter.class, consumer);
        assertInstanceOf(DubboContextProviderFilter.class, provider);
    }

    // 验证消费端只传播白名单上下文，并在调用结束后恢复原附件
    @Test
    void shouldPropagateOnlyWhitelistedContextAndRestoreOriginalAttachmentsOnConsumer() {
        RequestContext context = new RequestContext(
                "request-1", "operator", "9223372036854775807", "9223372036854775806",
                "CLIENT", "channel-a", "gray-a", "gateway", "zh-CN",
                "9223372036854775805", "external:access", "7");
        RpcContext.getClientAttachment().setAttachment(ContextKeys.REQUEST_ID, "parent-request");
        RpcContext.getClientAttachment().setAttachment("untrusted", "保留但不由上下文组件处理");

        try (ContextScope ignored = ContextAccessor.open(context)) {
            Result result = new DubboContextConsumerFilter().invoke(new CapturingInvoker(current -> {
                assertEquals("request-1", current.get(ContextKeys.REQUEST_ID));
                assertEquals("operator", current.get(ContextKeys.PRINCIPAL_NAME));
                assertEquals("9223372036854775807", current.get(ContextKeys.TENANT_ID));
                assertEquals("9223372036854775806", current.get(ContextKeys.USER_ID));
                assertEquals("CLIENT", current.get(ContextKeys.SUBJECT_TYPE));
                assertEquals("channel-a", current.get(ContextKeys.CLIENT_ID));
                assertEquals("9223372036854775805", current.get(ContextKeys.OWNER_ACCOUNT_ID));
                assertEquals("external:access", current.get(ContextKeys.AUTHORIZED_SCOPE));
                assertEquals("7", current.get(ContextKeys.CREDENTIAL_VERSION));
                assertEquals("gray-a", current.get(ContextKeys.GRAY_TAG));
                assertEquals("gateway", current.get(ContextKeys.SOURCE));
                assertEquals("zh-CN", current.get(ContextKeys.LOCALE));
                assertFalse(DubboContextAttachments.keys().contains("untrusted"));
            }), invocation);
            assertEquals("ok", result.getValue());
        }

        assertEquals("parent-request",
                RpcContext.getClientAttachment().getAttachment(ContextKeys.REQUEST_ID));
        assertEquals("保留但不由上下文组件处理",
                RpcContext.getClientAttachment().getAttachment("untrusted"));
    }

    // 验证消费端没有当前上下文时不会传播线程残留字段
    @Test
    void shouldNotPropagateStaleThreadValuesWhenConsumerHasNoContext() {
        RpcContext.getClientAttachment().setAttachment(ContextKeys.USER_ID, "stale-user");

        new DubboContextConsumerFilter().invoke(new CapturingInvoker(current ->
                assertNull(current.get(ContextKeys.USER_ID))), invocation);

        assertEquals("stale-user",
                RpcContext.getClientAttachment().getAttachment(ContextKeys.USER_ID));
    }

    // 验证消费端丢弃包含控制字符的附件值
    @Test
    void shouldDiscardAttachmentValuesContainingControlCharactersOnConsumer() {
        AtomicBoolean invoked = new AtomicBoolean();
        RequestContext invalid = context("request\nforged", null, null, null, null, null, null);

        try (ContextScope ignored = ContextAccessor.open(invalid)) {
            new DubboContextConsumerFilter().invoke(new CapturingInvoker(current -> {
                invoked.set(true);
                assertNull(current.get(ContextKeys.REQUEST_ID));
            }), invocation);
        }

        assertTrue(invoked.get());
    }

    // 验证提供端读取白名单上下文，并在调用结束后恢复父上下文
    @Test
    void shouldReadWhitelistedContextAndRestoreParentContextOnProvider() {
        RequestContext parent = context("parent", null, null, null, null, null, null);
        RpcContext.getServerAttachment().setAttachment(ContextKeys.REQUEST_ID, "request-2");
        RpcContext.getServerAttachment().setAttachment(ContextKeys.TENANT_ID, "tenant-2");
        RpcContext.getServerAttachment().setAttachment(ContextKeys.OWNER_ACCOUNT_ID, "owner-2");
        RpcContext.getServerAttachment().setAttachment(ContextKeys.AUTHORIZED_SCOPE, "external:access");
        RpcContext.getServerAttachment().setAttachment(ContextKeys.CREDENTIAL_VERSION, "8");
        RpcContext.getServerAttachment().setAttachment("untrusted", "不读取");
        AtomicReference<RequestContext> captured = new AtomicReference<>();

        try (ContextScope ignored = ContextAccessor.open(parent)) {
            new DubboContextProviderFilter().invoke(new CapturingInvoker(current ->
                    captured.set(ContextAccessor.current().orElseThrow())), invocation);
            assertEquals(parent, ContextAccessor.current().orElseThrow());
        }

        assertEquals("request-2", captured.get().requestId());
        assertEquals("tenant-2", captured.get().tenantId());
        assertEquals("owner-2", captured.get().ownerAccountId());
        assertEquals("external:access", captured.get().authorizedScope());
        assertEquals("8", captured.get().credentialVersion());
        assertNull(captured.get().userId());
    }

    // 验证提供端没有附件时隔离已有父上下文
    @Test
    void shouldIsolateParentContextWhenProviderHasNoAttachments() {
        RequestContext parent = context("parent", null, null, null, null, null, null);
        AtomicBoolean contextPresent = new AtomicBoolean(true);

        try (ContextScope ignored = ContextAccessor.open(parent)) {
            new DubboContextProviderFilter().invoke(new CapturingInvoker(current ->
                    contextPresent.set(ContextAccessor.current().isPresent())), invocation);
            assertEquals(parent, ContextAccessor.current().orElseThrow());
        }

        assertFalse(contextPresent.get());
    }

    // 验证提供端丢弃超长附件后仍继续调用业务方法
    @Test
    void shouldDiscardOversizedAttachmentAndContinueInvocationOnProvider() {
        AtomicBoolean invoked = new AtomicBoolean();
        RpcContext.getServerAttachment().setAttachment(
                ContextKeys.GRAY_TAG, "a".repeat(129));

        new DubboContextProviderFilter().invoke(new CapturingInvoker(current -> {
            invoked.set(true);
            assertTrue(ContextAccessor.current().isEmpty());
        }), invocation);

        assertTrue(invoked.get());
    }

    // 验证提供端业务调用异常后仍能恢复父上下文
    @Test
    void shouldRestoreParentContextAfterProviderInvocationFails() {
        RequestContext parent = context("parent", "caller", null, null, null, "service", null);
        RpcContext.getServerAttachment().setAttachment(ContextKeys.REQUEST_ID, "child");

        try (ContextScope ignored = ContextAccessor.open(parent)) {
            assertThrows(IllegalStateException.class, () -> new DubboContextProviderFilter().invoke(
                    new CapturingInvoker(current -> {
                        assertEquals("child", ContextAccessor.current().orElseThrow().requestId());
                        throw new IllegalStateException("测试异常");
                    }), invocation));
            assertEquals(parent, ContextAccessor.current().orElseThrow());
        }

        assertFalse(ContextAccessor.current().isPresent());
    }

    private static RequestContext context(
            String requestId,
            String principalName,
            String tenantId,
            String userId,
            String grayTag,
            String source,
            String locale
    ) {
        return new RequestContext(requestId, principalName, tenantId, userId, null, null, grayTag, source, locale,
                null, null, null);
    }

    private static final class CapturingInvoker implements Invoker<Object> {

        private final Consumer<Map<String, String>> assertion;

        private CapturingInvoker(Consumer<Map<String, String>> assertion) {
            this.assertion = assertion;
        }

        @Override
        public Class<Object> getInterface() {
            return Object.class;
        }

        @Override
        public URL getUrl() {
            return URL.valueOf("tri://127.0.0.1:50051/cn.iantech.TestService");
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public Result invoke(Invocation invocation) {
            assertion.accept(RpcContext.getClientAttachment().getAttachments());
            return new AppResponse("ok");
        }

        @Override
        public void destroy() {
        }
    }
}
