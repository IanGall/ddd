package cn.iantech.infrastructure.persistent.repository;

import cn.iantech.common.exception.AppException;
import cn.iantech.domain.rbac.infra.IRbacPermissionRepository;
import cn.iantech.domain.rbac.model.entity.RbacPermissionEntity;
import cn.iantech.infrastructure.persistent.dao.IRbacPermissionDao;
import cn.iantech.infrastructure.persistent.po.RbacPermissionPO;
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
public class RbacPermissionRepository implements IRbacPermissionRepository {

    private final IRbacPermissionDao rbacPermissionDao;
    private final Converter converter;

    @Override
    public RbacPermissionEntity save(Long accountId, RbacPermissionEntity entity) {
        RbacPermissionPO rbacPermissionPO = converter.convert(entity, RbacPermissionPO.class);
        rbacPermissionPO.setAccountId(accountId);
        LocalDateTime now = LocalDateTime.now();
        rbacPermissionPO.setCreateTime(now);
        rbacPermissionPO.setUpdateTime(now);
        try {
            rbacPermissionDao.insert(rbacPermissionPO);
        } catch (DuplicateKeyException exception) {
            // 并发写入唯一索引兜底，避免裸 DuplicateKeyException 直接 500
            throw new AppException(CONFLICT.getCode(), "权限编码已存在");
        }
        return converter.convert(rbacPermissionPO, RbacPermissionEntity.class);
    }

    @Override
    public void saveAll(Long accountId, List<RbacPermissionEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        // 与单条 save 保持同一套语义：账号归属以参数为准、补齐时间字段、不入参自增主键
        LocalDateTime now = LocalDateTime.now();
        List<RbacPermissionPO> poList = entities.stream()
                .map(entity -> {
                    RbacPermissionPO po = converter.convert(entity, RbacPermissionPO.class);
                    po.setId(null);
                    po.setAccountId(accountId);
                    po.setCreateTime(now);
                    po.setUpdateTime(now);
                    return po;
                })
                .toList();
        try {
            rbacPermissionDao.insertBatch(poList);
        } catch (DuplicateKeyException exception) {
            // 与单条写入同语义（409）；异常继续抛出，由调用方事务整体回滚
            throw new AppException(CONFLICT.getCode(), "权限编码已存在");
        }
    }

    @Override
    public Optional<RbacPermissionEntity> findById(Long accountId, Long id) {
        return Optional.ofNullable(rbacPermissionDao.selectById(accountId, id))
                .map(po -> converter.convert(po, RbacPermissionEntity.class));
    }

    @Override
    public List<RbacPermissionEntity> findAllByAccountId(Long accountId) {
        List<RbacPermissionPO> poList = Optional.ofNullable(rbacPermissionDao.selectAllByAccount(accountId))
                .orElse(List.of());
        return poList.stream()
                .map(po -> converter.convert(po, RbacPermissionEntity.class))
                .toList();
    }

    @Override
    public Optional<RbacPermissionEntity> findByPermCode(Long accountId, String permCode) {
        return Optional.ofNullable(rbacPermissionDao.selectByPermCode(accountId, permCode))
                .map(po -> converter.convert(po, RbacPermissionEntity.class));
    }

    @Override
    public long countPage(Long accountId, String permCode, String permName, Integer permType, Long parentId, Boolean status) {
        return rbacPermissionDao.selectPageCount(accountId, permCode, permName, permType, parentId, status);
    }

    @Override
    public List<RbacPermissionEntity> queryPage(Long accountId,
                                                String permCode,
                                                String permName,
                                                Integer permType,
                                                Long parentId,
                                                Boolean status,
                                                Integer offset,
                                                Integer pageSize) {
        List<RbacPermissionPO> poList = Optional.ofNullable(rbacPermissionDao.selectPage(
                        accountId,
                        permCode,
                        permName,
                        permType,
                        parentId,
                        status,
                        offset,
                        pageSize
                ))
                .orElse(List.of());

        return poList.stream()
                .map(po -> converter.convert(po, RbacPermissionEntity.class))
                .toList();
    }

    @Override
    public int updateById(Long accountId, RbacPermissionEntity entity) {
        RbacPermissionPO po = converter.convert(entity, RbacPermissionPO.class);
        po.setAccountId(accountId);
        return rbacPermissionDao.updateById(accountId, po);
    }

    @Override
    public int logicDeleteById(Long accountId, Long id) {
        return rbacPermissionDao.logicDeleteById(accountId, id);
    }

    @Override
    public List<Long> queryExistingIds(Long accountId, List<Long> permissionIds) {
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return Optional.ofNullable(rbacPermissionDao.selectExistingIds(accountId, permissionIds))
                .orElse(List.of());
    }

}
