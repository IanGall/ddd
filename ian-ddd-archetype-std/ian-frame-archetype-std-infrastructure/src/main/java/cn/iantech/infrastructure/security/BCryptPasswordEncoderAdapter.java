package cn.iantech.infrastructure.security;

import cn.dev33.satoken.secure.BCrypt;
import cn.iantech.domain.auth.infra.IPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Sa-Token BCrypt 适配器，技术实现留在基础设施层。
 */
@Component
public class BCryptPasswordEncoderAdapter implements IPasswordEncoder {

    @Override
    public String encode(CharSequence rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("原始密码不能为空");
        }
        return BCrypt.hashpw(rawPassword.toString());
    }

    @Override
    public boolean matches(CharSequence rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword.toString(), passwordHash);
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
