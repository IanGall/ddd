package cn.iantech.domain.rbac.service.impl;

import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.domain.rbac.infra.IRbacAccountRepository;
import cn.iantech.domain.rbac.infra.IRbacAuthorizationRepository;
import cn.iantech.domain.rbac.infra.IRbacPermissionRepository;
import cn.iantech.domain.rbac.infra.IRbacRoleRepository;
import cn.iantech.domain.rbac.model.RbacPermissionCode;
import cn.iantech.domain.rbac.service.IRbacAccessControlService;
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
public class RbacAccessControlService implements IRbacAccessControlService {

    private final IRbacAccountRepository accountRepository;
    private final IRbacAuthorizationRepository authorizationRepository;
    private final IRbacPermissionRepository permissionRepository;
    private final IRbacRoleRepository roleRepository;

    @Override
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

    @Override
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

    @Override
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

    @Override
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
