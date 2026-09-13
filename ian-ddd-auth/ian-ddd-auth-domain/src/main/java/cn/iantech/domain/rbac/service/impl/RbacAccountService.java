package cn.iantech.domain.rbac.service.impl;

import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.domain.auth.infra.IPasswordEncoder;
import cn.iantech.domain.rbac.infra.IRbacAccountRepository;
import cn.iantech.domain.rbac.infra.IRbacPermissionRepository;
import cn.iantech.domain.rbac.model.RbacPermissionCode;
import cn.iantech.domain.rbac.model.entity.RbacAccountEntity;
import cn.iantech.domain.rbac.model.entity.RbacPermissionEntity;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Service
public class RbacAccountService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{1,64}$");
    private static final int MIN_PASSWORD_BYTES = 8;
    private static final int MAX_PASSWORD_BYTES = 72;
    private static final int MAX_DISPLAY_NAME_LENGTH = 128;
    private static final int MAX_EMAIL_LENGTH = 128;
    private static final int MAX_MOBILE_LENGTH = 32;
    private final IRbacAccountRepository accountRepository;
    private final IRbacPermissionRepository permissionRepository;
    private final IPasswordEncoder passwordEncoder;

    public RbacAccountEntity createAccount(String username, String password, String displayName,
                                           String email, String mobile) {
        String finalUsername = StringUtils.trimToNull(username);
        if (finalUsername == null || !USERNAME_PATTERN.matcher(finalUsername).matches()) {
            throw illegalParameter("主账号用户名格式非法");
        }
        checkPassword(password);
        String finalDisplayName = normalizeOptionalText(displayName, "显示名称", MAX_DISPLAY_NAME_LENGTH);
        String finalEmail = normalizeOptionalText(email, "邮箱", MAX_EMAIL_LENGTH);
        String finalMobile = normalizeOptionalText(mobile, "手机号", MAX_MOBILE_LENGTH);
        RbacAccountEntity saved = accountRepository.save(RbacAccountEntity.builder()
                .username(finalUsername)
                .passwordHash(passwordEncoder.encode(password))
                .displayName(finalDisplayName)
                .email(finalEmail)
                .mobile(finalMobile)
                .status(Boolean.TRUE)
                .deleted(Boolean.FALSE)
                .build());
        if (Objects.isNull(saved.getId())) {
            throw illegalParameter("创建主账号失败");
        }
        Arrays.stream(RbacPermissionCode.values())
                .map(permission -> RbacPermissionEntity.builder()
                        .accountId(saved.getId())
                        .permCode(permission.getCode())
                        .permName(permission.getDescription())
                        .permType(2)
                        .parentId(0L)
                        .path("")
                        .method("")
                        .status(Boolean.TRUE)
                        .systemManaged(Boolean.TRUE)
                        .deleted(Boolean.FALSE)
                        .build())
                .forEach(permission -> permissionRepository.save(saved.getId(), permission));
        return saved;
    }

    private AppException illegalParameter(String info) {
        return new AppException(Constants.ResponseCode.INVALID_ARGUMENT.getCode(), info);
    }

    private void checkPassword(String password) {
        int passwordBytes = StringUtils.defaultString(password).getBytes(StandardCharsets.UTF_8).length;
        if (StringUtils.isBlank(password) || passwordBytes < MIN_PASSWORD_BYTES || passwordBytes > MAX_PASSWORD_BYTES) {
            throw illegalParameter("密码长度必须为 8 至 72 个 UTF-8 字节");
        }
    }

    private String normalizeOptionalText(String value, String fieldName, int maxLength) {
        String normalized = StringUtils.defaultString(StringUtils.trimToNull(value));
        int length = normalized.codePointCount(0, normalized.length());
        if (length > maxLength) {
            throw illegalParameter(fieldName + "长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

}
