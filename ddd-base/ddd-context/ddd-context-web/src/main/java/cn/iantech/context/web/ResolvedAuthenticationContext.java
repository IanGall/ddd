package cn.iantech.context.web;

/**
 * 认证边界解析出的可信身份信息，不直接暴露 Servlet 或安全框架对象。
 */
public record ResolvedAuthenticationContext(
        String principalName,
        String tenantId,
        String userId,
        String subjectType,
        String clientId,
        String ownerAccountId,
        String authorizedScope,
        String credentialVersion) {

    public static ResolvedAuthenticationContext empty() {
        return new ResolvedAuthenticationContext(null, null, null, null, null, null, null, null);
    }
}
