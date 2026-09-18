package cn.iantech.infrastructure.persistent.repository;

import cn.iantech.common.exception.AppException;
import cn.iantech.domain.rbac.infra.IRbacRoleRepository;
import cn.iantech.domain.rbac.model.entity.RbacRoleEntity;
import cn.iantech.infrastructure.persistent.dao.IRbacRoleDao;
import cn.iantech.infrastructure.persistent.po.RbacRolePO;
import io.github.linpeilie.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static cn.iantech.common.constant.Constants.ResponseCode.CONFLICT;

@Repository
@RequiredArgsConstructor
public class RbacRoleRepository implements IRbacRoleRepository {

    private final IRbacRoleDao rbacRoleDao;
    private final Converter converter;

    @Override
    public RbacRoleEntity save(Long accountId, RbacRoleEntity entity) {
        RbacRolePO rbacRolePO = converter.convert(entity, RbacRolePO.class);
        rbacRolePO.setAccountId(accountId);
        LocalDateTime now = LocalDateTime.now();
        rbacRolePO.setCreateTime(now);
        rbacRolePO.setUpdateTime(now);
        try {
            rbacRoleDao.insert(rbacRolePO);
        } catch (DuplicateKeyException exception) {
            // 并发写入唯一索引兜底，避免裸 DuplicateKeyException 直接 500
            throw new AppException(CONFLICT.getCode(), "角色编码已存在");
        }
        return converter.convert(rbacRolePO, RbacRoleEntity.class);
    }

    @Override
    public Optional<RbacRoleEntity> findById(Long accountId, Long id) {
        return Optional.ofNullable(rbacRoleDao.selectById(accountId, id))
                .map(po -> converter.convert(po, RbacRoleEntity.class));
    }

    @Override
    public Optional<RbacRoleEntity> findByRoleCode(Long accountId, String roleCode) {
        return Optional.ofNullable(rbacRoleDao.selectByRoleCode(accountId, roleCode))
                .map(po -> converter.convert(po, RbacRoleEntity.class));
    }

    @Override
    public long countPage(Long accountId, String roleCode, String roleName, Boolean status) {
        return rbacRoleDao.selectPageCount(accountId, roleCode, roleName, status);
    }

    @Override
    public List<RbacRoleEntity> queryPage(Long accountId, String roleCode, String roleName, Boolean status, Integer offset, Integer pageSize) {
        List<RbacRolePO> poList = Optional.ofNullable(rbacRoleDao.selectPage(accountId, roleCode, roleName, status, offset, pageSize))
                .orElse(List.of());
        return poList.stream()
                .map(po -> converter.convert(po, RbacRoleEntity.class))
                .toList();
    }

    @Override
    public int updateById(Long accountId, RbacRoleEntity entity) {
        RbacRolePO po = converter.convert(entity, RbacRolePO.class);
        po.setAccountId(accountId);
        try {
            return rbacRoleDao.updateById(accountId, po);
        } catch (DuplicateKeyException exception) {
            throw new AppException(CONFLICT.getCode(), "角色编码已存在");
        }
    }

    @Override
    public int logicDeleteById(Long accountId, Long id) {
        return rbacRoleDao.logicDeleteById(accountId, id);
    }

    @Override
    public List<Long> queryExistingIds(Long accountId, List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return Optional.ofNullable(rbacRoleDao.selectExistingIds(accountId, roleIds))
                .orElse(List.of());
    }

}
