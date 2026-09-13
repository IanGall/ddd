package cn.iantech.domain.rbac.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Admin RBAC 权限目录，是权限码、资源动作和系统初始化信息的唯一事实来源。
 *
 * <p>开户时按本目录逐条写入系统内置权限，因此新增权限码即等于为新账号默认初始化该权限。
 * 渠道凭证权限码（{@code rbac:channel-credential:*}）同样登记在此，与
 * {@code docs/dev-ops/environment/sql/channel-credential.sql} 的历史账号补种脚本保持一致。</p>
 */
@Getter
@RequiredArgsConstructor
public enum RbacPermissionCode {

    USER_READ("rbac:user:read", "user", "read", "查看子账号", RiskLevel.NORMAL),
    USER_CREATE("rbac:user:create", "user", "create", "创建子账号", RiskLevel.HIGH),
    USER_UPDATE("rbac:user:update", "user", "update", "更新子账号", RiskLevel.HIGH),
    USER_DELETE("rbac:user:delete", "user", "delete", "删除子账号", RiskLevel.HIGH),
    USER_ROLE_READ("rbac:user-role:read", "user-role", "read", "查看用户角色", RiskLevel.NORMAL),
    USER_ROLE_GRANT("rbac:user-role:grant", "user-role", "grant", "授予用户角色", RiskLevel.CRITICAL),
    ROLE_READ("rbac:role:read", "role", "read", "查看角色", RiskLevel.NORMAL),
    ROLE_CREATE("rbac:role:create", "role", "create", "创建角色", RiskLevel.HIGH),
    ROLE_UPDATE("rbac:role:update", "role", "update", "更新角色", RiskLevel.HIGH),
    ROLE_DELETE("rbac:role:delete", "role", "delete", "删除角色", RiskLevel.HIGH),
    ROLE_PERMISSION_READ("rbac:role-permission:read", "role-permission", "read", "查看角色权限", RiskLevel.NORMAL),
    ROLE_PERMISSION_GRANT("rbac:role-permission:grant", "role-permission", "grant", "授予角色权限", RiskLevel.CRITICAL),
    PERMISSION_READ("rbac:permission:read", "permission", "read", "查看权限", RiskLevel.NORMAL),
    PERMISSION_CREATE("rbac:permission:create", "permission", "create", "创建权限", RiskLevel.CRITICAL),
    PERMISSION_UPDATE("rbac:permission:update", "permission", "update", "更新权限", RiskLevel.CRITICAL),
    PERMISSION_DELETE("rbac:permission:delete", "permission", "delete", "删除权限", RiskLevel.CRITICAL),
    CHANNEL_CREDENTIAL_READ("rbac:channel-credential:read", "channel-credential", "read", "查看渠道凭证", RiskLevel.NORMAL),
    CHANNEL_CREDENTIAL_CREATE("rbac:channel-credential:create", "channel-credential", "create", "创建渠道凭证", RiskLevel.HIGH),
    CHANNEL_CREDENTIAL_UPDATE("rbac:channel-credential:update", "channel-credential", "update", "更新渠道凭证", RiskLevel.HIGH),
    CHANNEL_CREDENTIAL_ROTATE("rbac:channel-credential:rotate", "channel-credential", "rotate", "轮换渠道密钥", RiskLevel.CRITICAL),
    CHANNEL_CREDENTIAL_DELETE("rbac:channel-credential:delete", "channel-credential", "delete", "删除渠道凭证", RiskLevel.HIGH);

    private static final Map<String, RbacPermissionCode> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(RbacPermissionCode::getCode, Function.identity()));

    private final String code;
    private final String resource;
    private final String action;
    private final String description;
    private final RiskLevel riskLevel;

    public static RbacPermissionCode require(String code) {
        RbacPermissionCode permission = BY_CODE.get(code);
        if (permission == null) {
            throw new IllegalArgumentException("未注册的 RBAC 权限码：" + code);
        }
        return permission;
    }

    public enum RiskLevel {
        NORMAL,
        HIGH,
        CRITICAL
    }
}
