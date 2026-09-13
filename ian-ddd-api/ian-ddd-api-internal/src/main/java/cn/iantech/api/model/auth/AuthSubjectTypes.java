package cn.iantech.api.model.auth;

/**
 * 认证主体类型常量。
 */
public final class AuthSubjectTypes {

    public static final String ADMIN = "ADMIN";
    public static final String ADMIN_PRIMARY = "ADMIN_PRIMARY";
    public static final String ADMIN_SUB_ACCOUNT = "ADMIN_SUB_ACCOUNT";
    public static final String CUSTOMER = "CUSTOMER";
    public static final String PLATFORM_CLIENT = "PLATFORM_CLIENT";

    private AuthSubjectTypes() {
    }
}
