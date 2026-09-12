package cn.iantech.domain.rbac.service;

import cn.iantech.domain.rbac.model.RbacPermissionCode;
import java.util.List;

public interface IRbacAccessControlService {

    void authorize(Long accountId, Long userId, String principalName, String permissionCode);

    default void authorize(Long accountId, Long userId, String principalName, RbacPermissionCode permissionCode) {
        authorize(accountId, userId, principalName, permissionCode == null ? null : permissionCode.getCode());
    }

    void authorizeRoleGrant(Long accountId, Long userId, String principalName, List<Long> roleIds);

    void authorizePermissionGrant(Long accountId, Long userId, String principalName, List<Long> permissionIds);

    void authorizePermissionManagement(Long accountId, Long userId, String principalName, Long permissionId);
}
