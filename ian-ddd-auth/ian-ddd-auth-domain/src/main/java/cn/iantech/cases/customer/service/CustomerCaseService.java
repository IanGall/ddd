package cn.iantech.cases.customer.service;

import cn.iantech.cases.customer.model.CustomerRegisterCommand;
import cn.iantech.common.exception.AppException;
import cn.iantech.domain.auth.infra.IPasswordEncoder;
import cn.iantech.domain.customer.infra.ICustomerUserRepository;
import cn.iantech.domain.customer.model.CustomerUserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * C 端用户注册与凭据校验用例。
 */
@Service
@RequiredArgsConstructor
public class CustomerCaseService {
    private final ICustomerUserRepository repository;
    private final IPasswordEncoder passwordEncoder;

    @Transactional(rollbackFor = Exception.class)
    public CustomerUserEntity register(CustomerRegisterCommand command) {
        if (command == null) {
            throw new AppException("INVALID_ARGUMENT", "请求不能为空");
        }
        String normalized = normalizeLoginName(command.loginName());
        validatePassword(command.password());
        if (repository.findByLoginName(normalized).isPresent()) {
            throw new AppException("INVALID_ARGUMENT", "登录账号已注册");
        }
        try {
            return repository.save(CustomerUserEntity.builder().loginName(normalized)
                    .passwordHash(passwordEncoder.encode(command.password()))
                    .displayName(command.displayName() == null ? "" : command.displayName())
                    .status(true).deleted(false).build());
        } catch (DuplicateKeyException exception) {
            // 并发注册命中 uk_customer_user_login_name，避免裸 DuplicateKeyException 直接 500
            throw new AppException("INVALID_ARGUMENT", "登录账号已注册");
        }
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
