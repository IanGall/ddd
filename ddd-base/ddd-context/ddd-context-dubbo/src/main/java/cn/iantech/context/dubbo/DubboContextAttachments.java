package cn.iantech.context.dubbo;

import cn.iantech.context.core.ContextKeys;
import cn.iantech.context.core.ContextValidator;
import cn.iantech.context.core.RequestContext;
import org.apache.dubbo.rpc.RpcContextAttachment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dubbo 请求上下文附件协议。
 *
 * <p>该类只接收固定白名单字段，避免业务对象、登录凭证或任意附件进入 RPC 请求头。</p>
 */
public final class DubboContextAttachments {

    private static final List<Binding> BINDINGS = List.of(
            new Binding(ContextKeys.REQUEST_ID, RequestContext::requestId, ContextValidator::validOrNull),
            new Binding(ContextKeys.PRINCIPAL_NAME, RequestContext::principalName, ContextValidator::validOrNull),
            new Binding(ContextKeys.TENANT_ID, RequestContext::tenantId, ContextValidator::validOrNull),
            new Binding(ContextKeys.USER_ID, RequestContext::userId, ContextValidator::validOrNull),
            new Binding(ContextKeys.SUBJECT_TYPE, RequestContext::subjectType, ContextValidator::validOrNull),
            new Binding(ContextKeys.CLIENT_ID, RequestContext::clientId, ContextValidator::validOrNull),
            new Binding(ContextKeys.OWNER_ACCOUNT_ID, RequestContext::ownerAccountId, ContextValidator::validOrNull),
            new Binding(ContextKeys.AUTHORIZED_SCOPE, RequestContext::authorizedScope, ContextValidator::validOrNull),
            new Binding(ContextKeys.CREDENTIAL_VERSION, RequestContext::credentialVersion, ContextValidator::validOrNull),
            new Binding(ContextKeys.GRAY_TAG, RequestContext::grayTag, ContextValidator::validOrNull),
            new Binding(ContextKeys.SOURCE, RequestContext::source, ContextValidator::validOrNull),
            new Binding(ContextKeys.LOCALE, RequestContext::locale, ContextValidator::validLocaleOrNull)
    );

    private static final Map<String, Binding> BINDINGS_BY_KEY = BINDINGS.stream()
            .collect(Collectors.toUnmodifiableMap(Binding::key, Function.identity()));

    private static final Set<String> KEYS = BINDINGS_BY_KEY.keySet();

    private DubboContextAttachments() {
    }

    /**
     * 返回不可变的上下文附件白名单。
     */
    public static Set<String> keys() {
        return KEYS;
    }

    static RequestContext read(RpcContextAttachment attachment) {
        return new RequestContext(
                read(attachment, ContextKeys.REQUEST_ID),
                read(attachment, ContextKeys.PRINCIPAL_NAME),
                read(attachment, ContextKeys.TENANT_ID),
                read(attachment, ContextKeys.USER_ID),
                read(attachment, ContextKeys.SUBJECT_TYPE),
                read(attachment, ContextKeys.CLIENT_ID),
                read(attachment, ContextKeys.GRAY_TAG),
                read(attachment, ContextKeys.SOURCE),
                read(attachment, ContextKeys.LOCALE),
                read(attachment, ContextKeys.OWNER_ACCOUNT_ID),
                read(attachment, ContextKeys.AUTHORIZED_SCOPE),
                read(attachment, ContextKeys.CREDENTIAL_VERSION)
        );
    }

    static void write(RequestContext context, RpcContextAttachment attachment) {
        clear(attachment);
        BINDINGS.stream()
                .map(binding -> new Value(binding, binding.validator().apply(binding.extractor().apply(context))))
                .filter(value -> value.value() != null)
                .forEach(value -> attachment.setAttachment(value.binding().key(), value.value()));
    }

    static Map<String, String> snapshot(RpcContextAttachment attachment) {
        return BINDINGS.stream()
                .filter(binding -> attachment.getAttachment(binding.key()) != null)
                .collect(Collectors.toMap(
                        Binding::key,
                        binding -> attachment.getAttachment(binding.key()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    static void restore(RpcContextAttachment attachment, Map<String, String> snapshot) {
        clear(attachment);
        snapshot.forEach(attachment::setAttachment);
    }

    static void clear(RpcContextAttachment attachment) {
        BINDINGS.stream()
                .map(Binding::key)
                .forEach(attachment::removeAttachment);
    }

    private static String read(RpcContextAttachment attachment, String key) {
        Binding binding = BINDINGS_BY_KEY.get(key);
        return binding.validator().apply(attachment.getAttachment(key));
    }

    private record Binding(
            String key,
            Function<RequestContext, String> extractor,
            Function<String, String> validator
    ) {
    }

    private record Value(Binding binding, String value) {
    }
}
