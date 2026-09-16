package cn.iantech.infrastructure.id;

/**
 * Auth 服务的 ID 生成器业务名。
 *
 * <p>这些值是**编译期常量**，因此可以直接用在持久化对象的 {@code @IdGenerator} 注解上
 * （注解参数不能是方法调用）。
 *
 * <p>业务名必须与 {@code ddd.id-generator.businesses} 中声明的键一致：声明缺失或拼写不一致时，
 * 应用启动阶段取生成器就会失败，不会退化到共用序列。
 *
 * <p><b>划界的唯一判据是「ID 是否流入同一个字段或同一列」。</b>同一应用实例内所有业务共用
 * 同一个 Worker ID 与同一套序列起点，因此两个业务的生成器会产出相同的数值序列；
 * 凡是可能出现在同一个标识字段里的表，必须共用同一个业务生成器。
 *
 * <p>{@link #IDENTITY} 就是按这条判据合并出来的：{@code rbac_account.id}、{@code rbac_user.id}
 * 与 {@code customer_user.id} 都会流进 {@code AuthSession.userId}，再由 {@code userType} 分派，
 * 所以它们必须共用一个序列，才能保证该字段在服务内唯一。
 */
public final class AuthIdBusiness {

    /** 身份标识：{@code rbac_account} / {@code rbac_user} / {@code customer_user} 共用一个序列。 */
    public static final String IDENTITY = "identity";

    /** 登录会话标识（Redis 里的 sessionId / familyId，不是表主键，因此不参与注解填充）。 */
    public static final String AUTH_SESSION = "auth-session";

    /** 渠道凭证标识。 */
    public static final String CHANNEL_CREDENTIAL = "channel-credential";

    /**
     * 分片订单表 {@code user_order} 的标识。
     *
     * <p>单独一个业务而不是并入 {@link #IDENTITY}：它的 id 不会流进任何身份字段，按划界判据不该合并。
     * 分片表按 {@code user_id} 路由，与 id 无关，所以应用侧提前生成主键不影响路由。
     */
    public static final String USER_ORDER = "user-order";

    private AuthIdBusiness() {
    }
}
