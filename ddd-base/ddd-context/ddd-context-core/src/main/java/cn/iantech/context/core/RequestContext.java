package cn.iantech.context.core;

/**
 * 一次请求或 RPC 调用的不可变上下文，只承载跨边界所需的少量标量字段。
 */
public record RequestContext(
        String requestId,
        String principalName,
        String tenantId,
        String userId,
        String subjectType,
        String clientId,
        String grayTag,
        String source,
        String locale,
        String ownerAccountId,
        String authorizedScope,
        String credentialVersion) {

    /** 请求来源：网关转发的可信请求。 */
    public static final String SOURCE_GATEWAY = "gateway";

    private static final RequestContext EMPTY = new RequestContext(
            null, null, null, null, null, null, null, null, null, null, null, null);

    public RequestContext {
        requestId = ContextValidator.validOrNull(requestId);
        principalName = ContextValidator.validOrNull(principalName);
        tenantId = ContextValidator.validOrNull(tenantId);
        userId = ContextValidator.validOrNull(userId);
        subjectType = ContextValidator.validOrNull(subjectType);
        clientId = ContextValidator.validOrNull(clientId);
        ownerAccountId = ContextValidator.validOrNull(ownerAccountId);
        authorizedScope = ContextValidator.validOrNull(authorizedScope);
        credentialVersion = ContextValidator.validOrNull(credentialVersion);
        grayTag = ContextValidator.validOrNull(grayTag);
        source = ContextValidator.validOrNull(source);
        locale = ContextValidator.validLocaleOrNull(locale);
    }

    public static RequestContext empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return EMPTY.equals(this);
    }
}
