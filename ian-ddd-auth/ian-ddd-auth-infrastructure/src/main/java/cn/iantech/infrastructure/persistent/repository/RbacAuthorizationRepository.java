package cn.iantech.infrastructure.persistent.repository;

import cn.iantech.domain.rbac.infra.IRbacAuthorizationRepository;
import cn.iantech.infrastructure.persistent.dao.IRbacAuthorizationDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class RbacAuthorizationRepository implements IRbacAuthorizationRepository {

    private final IRbacAuthorizationDao authorizationDao;

    @Override
    public boolean existsPermission(Long accountId, Long userId, String permissionCode) {
        return authorizationDao.existsPermission(accountId, userId, permissionCode);
    }

    @Override
    public List<String> findRoleCodes(Long accountId, Long userId) {
        return authorizationDao.findRoleCodes(accountId, userId);
    }

    @Override
    public List<String> findPermissionCodes(Long accountId, Long userId) {
        return authorizationDao.findPermissionCodes(accountId, userId);
    }

    @Override
    public List<String> findPermissionCodesByRoleIds(Long accountId, List<Long> roleIds) {
        return authorizationDao.findPermissionCodesByRoleIds(accountId, roleIds);
    }

    @Override
    public List<String> findPermissionCodesByIds(Long accountId, List<Long> permissionIds) {
        return authorizationDao.findPermissionCodesByIds(accountId, permissionIds);
    }
}
