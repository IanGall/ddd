package cn.iantech.infrastructure.persistent.repository;

import cn.iantech.domain.rbac.infra.IRbacRelationRepository;
import cn.iantech.infrastructure.persistent.dao.IRbacRolePermissionDao;
import cn.iantech.infrastructure.persistent.dao.IRbacUserRoleDao;
import cn.iantech.infrastructure.persistent.po.RbacRolePermissionPO;
import cn.iantech.infrastructure.persistent.po.RbacUserRolePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RbacRelationRepository implements IRbacRelationRepository {

    private final IRbacUserRoleDao rbacUserRoleDao;
    private final IRbacRolePermissionDao rbacRolePermissionDao;

    @Override
    public List<Long> queryRoleIdsByUserId(Long accountId, Long userId) {
        return Optional.ofNullable(rbacUserRoleDao.selectRoleIdsByUserId(accountId, userId))
                .orElse(List.of());
    }

    @Override
    public int deleteUserRoles(Long accountId, Long userId, List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return 0;
        }
        return rbacUserRoleDao.deleteByUserIdAndRoleIds(accountId, userId, roleIds);
    }

    @Override
    public int insertUserRoles(Long accountId, Long userId, List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return 0;
        }

        List<RbacUserRolePO> poList = roleIds.stream()
                .map(roleId -> RbacUserRolePO.builder()
                        .accountId(accountId)
                        .userId(userId)
                        .roleId(roleId)
                        .build())
                .toList();

        return rbacUserRoleDao.batchInsert(poList);
    }

    @Override
    public List<Long> queryPermissionIdsByRoleId(Long accountId, Long roleId) {
        return Optional.ofNullable(rbacRolePermissionDao.selectPermissionIdsByRoleId(accountId, roleId))
                .orElse(List.of());
    }

    @Override
    public int deleteRolePermissions(Long accountId, Long roleId, List<Long> permissionIds) {
        if (permissionIds.isEmpty()) {
            return 0;
        }
        return rbacRolePermissionDao.deleteByRoleIdAndPermissionIds(accountId, roleId, permissionIds);
    }

    @Override
    public int insertRolePermissions(Long accountId, Long roleId, List<Long> permissionIds) {
        if (permissionIds.isEmpty()) {
            return 0;
        }

        List<RbacRolePermissionPO> poList = permissionIds.stream()
                .map(permissionId -> RbacRolePermissionPO.builder()
                        .accountId(accountId)
                        .roleId(roleId)
                        .permissionId(permissionId)
                        .build())
                .toList();

        return rbacRolePermissionDao.batchInsert(poList);
    }

    @Override
    public int deleteAllUserRoles(Long accountId, Long userId) {
        return rbacUserRoleDao.deleteAllByUserId(accountId, userId);
    }

    @Override
    public int deleteAllRoleRelations(Long accountId, Long roleId) {
        return rbacUserRoleDao.deleteAllByRoleId(accountId, roleId)
                + rbacRolePermissionDao.deleteAllByRoleId(accountId, roleId);
    }

    @Override
    public int deleteAllPermissionRelations(Long accountId, Long permissionId) {
        return rbacRolePermissionDao.deleteAllByPermissionId(accountId, permissionId);
    }

}
