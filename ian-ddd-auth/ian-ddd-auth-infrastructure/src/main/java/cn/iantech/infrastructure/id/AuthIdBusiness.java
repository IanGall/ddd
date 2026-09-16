package cn.iantech.infrastructure.id;

/**
 * Auth 服务按业务划分的 ID 生成器。
 *
 * <p>业务名必须与 {@code ddd.id-generator.businesses} 中声明的键一致：声明缺失或拼写不一致时，
 * 应用启动阶段取生成器就会失败，不会退化到共用 Worker ID 区间。
 *
 * <p><b>划界的唯一判据是「ID 是否流入同一个字段或同一列」。</b>同一应用实例内所有业务共用
 * 同一个 Worker ID 与同一套序列起点，因此两个业务的生成器会产出相同的数值序列；
 * 凡是可能出现在同一个标识字段里的表，必须共用同一个业务生成器。
 *
 * <p>{@link #IDENTITY} 就是按这条判据合并出来的：{@code rbac_account.id}、{@code rbac_user.id}
 * 与 {@code customer_user.id} 都会流进 {@code AuthSession.userId}，再由 {@code userType} 分派，
 * 所以它们必须共用一个序列，才能保证该字段在服务内唯一。
 */
public enum AuthIdBusiness {

    /** 身份标识：{@code rbac_account} / {@code rbac_user} / {@code customer_user} 共用一个序列。 */
    IDENTITY("identity"),

    /** 登录会话标识。 */
    AUTH_SESSION("auth-session"),

    /** 渠道凭证标识。 */
    CHANNEL_CREDENTIAL("channel-credential");

    private final String businessName;

    AuthIdBusiness(String businessName) {
        this.businessName = businessName;
    }

    public String businessName() {
        return businessName;
    }
}
