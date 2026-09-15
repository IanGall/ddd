package cn.iantech.domain.rbac.service.impl;

import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.domain.rbac.infra.IRbacAccountRepository;
import cn.iantech.domain.rbac.infra.IRbacAuthorizationRepository;
import cn.iantech.domain.rbac.infra.IRbacPermissionRepository;
import cn.iantech.domain.rbac.infra.IRbacRoleRepository;
import cn.iantech.domain.rbac.model.RbacPermissionCode;
import cn.iantech.domain.rbac.model.entity.RbacPermissionEntity;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 主账号拥有账号内全部权限，子账号按权限码授权。
 */
@RequiredArgsConstructor
@Service
public class RbacAccessControlService {

    private final IRbacAccountRepository accountRepository;
    private final IRbacAuthorizationRepository authorizationRepository;
    private final IRbacPermissionRepository permissionRepository;
    private final IRbacRoleRepository roleRepository;

    public void authorize(Long accountId, Long userId, String principalName, RbacPermissionCode permissionCode) {
        authorize(accountId, userId, principalName, permissionCode == null ? null : permissionCode.getCode());
    }

    public void authorize(Long accountId, Long userId, String principalName, String permissionCode) {
        if (accountId == null || accountId <= 0 || userId == null || userId <= 0
                || StringUtils.isBlank(principalName) || StringUtils.isBlank(permissionCode)) {
            throw unauthorized();
        }
        // 主账号停用后，已签发的子账号令牌也必须立即失效。
        if (accountRepository.findById(accountId).isEmpty()) {
            throw unauthorized();
        }
        boolean primaryAccount = isPrimaryAccount(accountId, userId, principalName);
        if (!primaryAccount && !authorizationRepository.existsPermission(accountId, userId, permissionCode)) {
            throw unauthorized();
        }
    }

    public void authorizeRoleGrant(Long accountId, Long userId, String principalName, List<Long> roleIds) {
        authorize(accountId, userId, principalName, RbacPermissionCode.USER_ROLE_GRANT);
        if (isPrimaryAccount(accountId, userId, principalName) || roleIds == null || roleIds.isEmpty()) {
            return;
        }
        List<Long> normalizedRoleIds = roleIds.stream().filter(Objects::nonNull).distinct().toList();
        if (roleRepository.queryExistingIds(accountId, normalizedRoleIds).size() != normalizedRoleIds.size()) {
            throw unauthorized();
        }
        Set<String> ownedPermissions = Set.copyOf(authorizationRepository.findPermissionCodes(accountId, userId));
        boolean allowed = authorizationRepository.findPermissionCodesByRoleIds(accountId, normalizedRoleIds).stream()
                .allMatch(ownedPermissions::contains);
        if (!allowed) {
            throw unauthorized();
        }
    }

    public void authorizePermissionGrant(Long accountId, Long userId, String principalName, List<Long> permissionIds) {
        authorize(accountId, userId, principalName, RbacPermissionCode.ROLE_PERMISSION_GRANT);
        if (isPrimaryAccount(accountId, userId, principalName) || permissionIds == null || permissionIds.isEmpty()) {
            return;
        }
        List<Long> normalizedPermissionIds = permissionIds.stream().filter(Objects::nonNull).distinct().toList();
        if (permissionRepository.queryExistingIds(accountId, normalizedPermissionIds).size() != normalizedPermissionIds.size()) {
            throw unauthorized();
        }
        Set<String> ownedPermissions = Set.copyOf(authorizationRepository.findPermissionCodes(accountId, userId));
        boolean allowed = authorizationRepository.findPermissionCodesByIds(accountId, normalizedPermissionIds).stream()
                .allMatch(ownedPermissions::contains);
        if (!allowed) {
            throw unauthorized();
        }
    }

    public void authorizePermissionManagement(Long accountId, Long userId, String principalName, Long permissionId) {
        if (accountRepository.findById(accountId).isEmpty()) {
            throw unauthorized();
        }
        if (isPrimaryAccount(accountId, userId, principalName)) {
            return;
        }
        if (permissionId == null || permissionId <= 0) {
            throw unauthorized();
        }
        Set<String> ownedPermissions = Set.copyOf(authorizationRepository.findPermissionCodes(accountId, userId));
        boolean allowed = permissionRepository.findById(accountId, permissionId)
                .map(permission -> ownedPermissions.contains(permission.getPermCode()))
                .orElse(false);
        if (!allowed) {
            throw unauthorized();
        }
    }

    /**
     * 查询主体在当前账号下的有效权限码（去重、升序）。
     *
     * <p>与 {@link #authorize} 的放行语义严格一致：主账号拥有账号内全部权限，子账号按角色聚合。
     * 两条路径都实时读库，因此撤销关系或停用账号/用户即时生效。</p>
     *
     * <p><b>主账号不过滤权限的 status</b>：{@link #authorize} 对主账号是无条件放行，不看权限行的状态，
     * 若此处按 status 过滤就会出现「接口能调通、菜单却不显示」的错位。因此主账号返回账号内**全部**权限码
     * （含自定义权限），过滤只对子账号生效（其查询已在 SQL 内完成用户/角色/权限的停用与软删除过滤）。</p>
     */
    public List<String> findEffectivePermissionCodes(Long accountId, Long userId, String principalName) {
        if (accountId == null || accountId <= 0 || userId == null || userId <= 0
                || StringUtils.isBlank(principalName)) {
            throw unauthorized();
        }
        // 主账号停用后，已签发的子账号令牌也必须立即失效（与 authorize 同一前提）
        if (accountRepository.findById(accountId).isEmpty()) {
            throw unauthorized();
        }
        List<String> codes = isPrimaryAccount(accountId, userId, principalName)
                // 主账号没有 rbac_user_role 关系，权限事实来源是账号内的权限目录（已过滤软删除）
                ? permissionRepository.findAllByAccountId(accountId).stream()
                        .map(RbacPermissionEntity::getPermCode)
                        .toList()
                // 子账号走角色聚合：该查询已在 SQL 内过滤用户/角色/权限的停用与软删除
                : authorizationRepository.findPermissionCodes(accountId, userId);
        return codes.stream().filter(StringUtils::isNotBlank).distinct().sorted().toList();
    }

    /**
     * 账号存在性由调用方先行校验（authorize / authorizePermissionManagement），
     * 这里只做主账号身份判定，避免鉴权热路径重复查询账号表。
     */
    private boolean isPrimaryAccount(Long accountId, Long userId, String principalName) {
        return accountId.equals(userId)
                && accountRepository.findByUsername(accountId, principalName).isPresent();
    }

    private AppException unauthorized() {
        return new AppException(Constants.ResponseCode.ACCESS_DENIED.getCode(), "账号权限不足");
    }
}
