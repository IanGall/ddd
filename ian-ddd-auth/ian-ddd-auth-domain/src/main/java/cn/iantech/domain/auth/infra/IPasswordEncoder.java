package cn.iantech.domain.auth.infra;

/**
 * 密码散列端口，领域层不依赖具体安全框架。
 */
public interface IPasswordEncoder {

    String encode(CharSequence rawPassword);

    boolean matches(CharSequence rawPassword, String passwordHash);
}
