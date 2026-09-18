package cn.iantech.domain.rbac.service.impl;

import cn.iantech.domain.rbac.infra.IRbacAccountRepository;
import cn.iantech.domain.rbac.infra.IRbacPermissionRepository;
import cn.iantech.domain.rbac.model.RbacPermissionCode;
import cn.iantech.domain.rbac.model.entity.RbacAccountEntity;
import cn.iantech.domain.rbac.model.entity.RbacPermissionEntity;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 主账号开户：创建账号并初始化内置权限目录。
 *
 * <p>用户名规则复用 {@link RbacValidationSupport}。口令的校验与 BCrypt 编码由调用方
 * （{@code RbacCaseService}）在<b>事务外</b>完成，本方法只接收已编码摘要——避免约 100ms 的
 * 慢哈希占用事务连接。</p>
 */
@RequiredArgsConstructor
@Service
public class RbacAccountService {

    private static final int MAX_DISPLAY_NAME_LENGTH = 128;
    private static final int MAX_EMAIL_LENGTH = 128;
    private static final int MAX_MOBILE_LENGTH = 32;
    private final IRbacAccountRepository accountRepository;
    private final IRbacPermissionRepository permissionRepository;

    /**
     * @param passwordHash 调用方在事务外完成编码的口令摘要，本方法不再做校验与编码
     */
    public RbacAccountEntity createAccount(String username, String passwordHash, String displayName,
                                           String email, String mobile) {
        String finalUsername = StringUtils.trimToNull(username);
        if (finalUsername == null || !RbacValidationSupport.USERNAME_PATTERN.matcher(finalUsername).matches()) {
            throw RbacValidationSupport.illegalParameter("主账号用户名格式非法");
        }
        String finalDisplayName = RbacValidationSupport.normalizeOptionalText(displayName, "显示名称",
                MAX_DISPLAY_NAME_LENGTH);
        String finalEmail = RbacValidationSupport.normalizeOptionalText(email, "邮箱", MAX_EMAIL_LENGTH);
        String finalMobile = RbacValidationSupport.normalizeOptionalText(mobile, "手机号", MAX_MOBILE_LENGTH);
        RbacAccountEntity saved = accountRepository.save(RbacAccountEntity.builder()
                .username(finalUsername)
                .passwordHash(passwordHash)
                .displayName(finalDisplayName)
                .email(finalEmail)
                .mobile(finalMobile)
                .status(Boolean.TRUE)
                .deleted(Boolean.FALSE)
                .build());
        if (Objects.isNull(saved.getId())) {
            throw RbacValidationSupport.illegalParameter("创建主账号失败");
        }
        List<RbacPermissionEntity> permissions = Arrays.stream(RbacPermissionCode.values())
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
                .toList();
        // 一次批量写入：内置权限目录共 21 条，逐条插入会产生同量级的 SQL 往返
        permissionRepository.saveAll(saved.getId(), permissions);
        return saved;
    }

}
