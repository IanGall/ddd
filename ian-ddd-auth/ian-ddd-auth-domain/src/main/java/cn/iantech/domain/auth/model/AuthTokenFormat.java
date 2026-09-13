package cn.iantech.domain.auth.model;

/**
 * Auth Token 格式与会话存储版本的唯一来源。
 *
 * <p>Token 形态与会话存储命名空间共用同一版本号：升级版本意味着旧 Token 与旧会话一并失效，
 * 两者不得各自维护版本常量。</p>
 */
public final class AuthTokenFormat {

    /**
     * Token 格式版本，同时用于推导会话存储命名空间。
     */
    public static final String VERSION = "v4";

    private AuthTokenFormat() {
    }
}
