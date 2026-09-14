package cn.iantech.cases.customer.service;

import cn.iantech.cases.customer.model.CustomerRegisterCommand;
import cn.iantech.common.exception.AppException;
import cn.iantech.domain.auth.infra.IPasswordEncoder;
import cn.iantech.domain.customer.infra.ICustomerUserRepository;
import cn.iantech.domain.customer.model.CustomerUserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * C 端用户注册与凭据校验用例。
 */
@Service
@RequiredArgsConstructor
public class CustomerCaseService {
    private final ICustomerUserRepository repository;
    private final IPasswordEncoder passwordEncoder;

    /**
     * C 端注册。
     *
     * <p>刻意**不加** {@code @Transactional}：本方法只有一次 INSERT，单语句自身即原子，
     * 而 BCrypt 慢哈希（约 100ms）若处在事务内会一直占用连接池连接——匿名注册是唯一
     * 无需认证即可触发的入口，长事务会被少量并发直接放大为连接池耗尽。
     * 登录名重复由 {@code uk_customer_user_login_name} 唯一索引兜底（见仓储实现）。</p>
     */
    public CustomerUserEntity register(CustomerRegisterCommand command) {
        if (command == null) {
            throw new AppException("INVALID_ARGUMENT", "请求不能为空");
        }
        String normalized = normalizeLoginName(command.loginName());
        validatePassword(command.password());
        if (repository.findByLoginName(normalized).isPresent()) {
            throw new AppException("INVALID_ARGUMENT", "登录账号已注册");
        }
        return repository.save(CustomerUserEntity.builder().loginName(normalized)
                .passwordHash(passwordEncoder.encode(command.password()))
                .displayName(command.displayName() == null ? "" : command.displayName())
                .status(true).deleted(false).build());
    }

    public CustomerUserEntity authenticate(String loginName, String password) {
        CustomerUserEntity user = repository.findByLoginName(normalizeLoginName(loginName))
                .orElseThrow(() -> new AppException("AUTH_REQUIRED", "账号或密码错误"));
        if (!Boolean.TRUE.equals(user.getStatus()) || Boolean.TRUE.equals(user.getDeleted())
                || password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AppException("AUTH_REQUIRED", "账号或密码错误");
        }
        return user;
    }

    private String normalizeLoginName(String value) {
        if (value == null) {
            throw new AppException("INVALID_ARGUMENT", "登录账号不合法");
        }
        String normalized = value.trim();
        if (!normalized.matches("[A-Za-z0-9_.-]{1,64}")) {
            throw new AppException("INVALID_ARGUMENT", "登录账号不合法");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new AppException("INVALID_ARGUMENT", "密码长度必须为8到72位");
        }
    }
}
