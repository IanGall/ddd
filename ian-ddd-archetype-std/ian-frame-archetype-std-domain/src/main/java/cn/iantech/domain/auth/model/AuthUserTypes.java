package cn.iantech.domain.auth.model;

/**
 * 认证身份的用户类型（userType）常量，跨 auth/rbac/customer 上下文共用。
 */
public final class AuthUserTypes {

    public static final String PRIMARY = "PRIMARY";
    public static final String SUB_ACCOUNT = "SUB_ACCOUNT";
    public static final String CUSTOMER = "CUSTOMER";

    private AuthUserTypes() {
    }
}
