package cn.iantech.context.core;

/** 统一的 Dubbo Attachment 键名与日志追踪键名。 */
public final class ContextKeys {

    /** 日志 MDC 键：logback 的 {@code %X{trace-id}} 使用该键输出请求追踪号。 */
    public static final String TRACE_ID = "trace-id";

    public static final String REQUEST_ID = "x-ctx-request-id";
    public static final String PRINCIPAL_NAME = "x-ctx-principal";
    public static final String TENANT_ID = "x-ctx-tenant-id";
    public static final String USER_ID = "x-ctx-user-id";
    public static final String SUBJECT_TYPE = "x-ctx-subject-type";
    public static final String CLIENT_ID = "x-ctx-client-id";
    public static final String OWNER_ACCOUNT_ID = "x-ctx-owner-account-id";
    public static final String AUTHORIZED_SCOPE = "x-ctx-authorized-scope";
    public static final String CREDENTIAL_VERSION = "x-ctx-credential-version";
    public static final String GRAY_TAG = "x-ctx-gray-tag";
    public static final String SOURCE = "x-ctx-source";
    public static final String LOCALE = "x-ctx-locale";

    private ContextKeys() {
    }
}
