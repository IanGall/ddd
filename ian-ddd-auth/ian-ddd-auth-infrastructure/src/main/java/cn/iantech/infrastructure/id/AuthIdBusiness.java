package cn.iantech.infrastructure.id;

/**
 * Auth 服务按业务划分的 ID 生成器。
 *
 * <p>业务名必须与 {@code ddd.id-generator.businesses} 中声明的键一致：声明缺失或拼写不一致时，
 * 应用启动阶段取生成器就会失败，不会退化到共用 Worker ID 区间。
 */
public enum AuthIdBusiness {

    AUTH_SESSION("auth-session"),
    RBAC_ACCOUNT("rbac-account"),
    RBAC_USER("rbac-user"),
    CUSTOMER_USER("customer-user"),
    CHANNEL_CREDENTIAL("channel-credential");

    private final String businessName;

    AuthIdBusiness(String businessName) {
        this.businessName = businessName;
    }

    public String businessName() {
        return businessName;
    }
}
