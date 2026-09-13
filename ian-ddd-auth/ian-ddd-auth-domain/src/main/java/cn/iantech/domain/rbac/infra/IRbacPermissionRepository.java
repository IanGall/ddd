package cn.iantech.domain.rbac.infra;

import cn.iantech.domain.rbac.model.entity.RbacPermissionEntity;

import java.util.List;
import java.util.Optional;

public interface IRbacPermissionRepository {

    RbacPermissionEntity save(Long accountId, RbacPermissionEntity entity);

    Optional<RbacPermissionEntity> findById(Long accountId, Long id);

    Optional<RbacPermissionEntity> findByPermCode(Long accountId, String permCode);

    List<RbacPermissionEntity> findAllByAccountId(Long accountId);

    long countPage(Long accountId, String permCode, String permName, Integer permType, Long parentId, Boolean status);

    List<RbacPermissionEntity> queryPage(Long accountId,
                                         String permCode,
                                         String permName,
                                         Integer permType,
                                         Long parentId,
                                         Boolean status,
                                         Integer offset,
                                         Integer pageSize);

    int updateById(Long accountId, RbacPermissionEntity entity);

    int logicDeleteById(Long accountId, Long id);

    List<Long> queryExistingIds(Long accountId, List<Long> permissionIds);

}
