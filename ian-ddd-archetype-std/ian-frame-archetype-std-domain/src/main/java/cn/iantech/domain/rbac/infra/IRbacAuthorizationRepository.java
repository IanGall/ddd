package cn.iantech.domain.rbac.infra;

import java.util.List;

public interface IRbacAuthorizationRepository {

    boolean existsPermission(Long accountId, Long userId, String permissionCode);

    List<String> findRoleCodes(Long accountId, Long userId);

    List<String> findPermissionCodes(Long accountId, Long userId);

    List<String> findPermissionCodesByRoleIds(Long accountId, List<Long> roleIds);

    List<String> findPermissionCodesByIds(Long accountId, List<Long> permissionIds);
}
