package cn.iantech.domain.rbac.service.impl;

import cn.iantech.domain.auth.infra.IPasswordEncoder;
import cn.iantech.domain.rbac.infra.IRbacAccountRepository;
import cn.iantech.domain.rbac.infra.IRbacPermissionRepository;
import cn.iantech.domain.rbac.model.RbacPermissionCode;
import cn.iantech.domain.rbac.model.entity.RbacAccountEntity;
import cn.iantech.domain.rbac.model.entity.RbacPermissionEntity;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Objects;

/**
 * 主账号开户：创建账号并初始化内置权限目录。
 *
 * <p>用户名与口令规则统一复用 {@link RbacValidationSupport}，避免同一规则在此处再维护一份
 * （历史上两处口令长度口径曾分别按字节与字符判断）。</p>
 */
@RequiredArgsConstructor
@Service
public class RbacAccountService {

    private static final int MAX_DISPLAY_NAME_LENGTH = 128;
    private static final int MAX_EMAIL_LENGTH = 128;
    private static final int MAX_MOBILE_LENGTH = 32;
    private final IRbacAccountRepository accountRepository;
    private final IRbacPermissionRepository permissionRepository;
    private final IPasswordEncoder passwordEncoder;

    public RbacAccountEntity createAccount(String username, String password, String displayName,
                                           String email, String mobile) {
        String finalUsername = StringUtils.trimToNull(username);
        if (finalUsername == null || !RbacValidationSupport.USERNAME_PATTERN.matcher(finalUsername).matches()) {
            throw RbacValidationSupport.illegalParameter("主账号用户名格式非法");
        }
        RbacValidationSupport.checkPassword(password);
        String finalDisplayName = RbacValidationSupport.normalizeOptionalText(displayName, "显示名称",
                MAX_DISPLAY_NAME_LENGTH);
        String finalEmail = RbacValidationSupport.normalizeOptionalText(email, "邮箱", MAX_EMAIL_LENGTH);
        String finalMobile = RbacValidationSupport.normalizeOptionalText(mobile, "手机号", MAX_MOBILE_LENGTH);
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
            throw RbacValidationSupport.illegalParameter("创建主账号失败");
        }
        Arrays.stream(RbacPermissionCode.values())
                .map(permission -> RbacPermissionEntity.builder()
                        .accountId(saved.getId())
                        .permCode(permission.getCode())
                        .permName(permission.getDescription())
                        .permType(RbacValidationSupport.DEFAULT_PERM_TYPE)
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

}
