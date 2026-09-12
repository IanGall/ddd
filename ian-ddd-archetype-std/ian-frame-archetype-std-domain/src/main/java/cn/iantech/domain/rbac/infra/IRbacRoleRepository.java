package cn.iantech.domain.rbac.infra;

import cn.iantech.domain.rbac.model.entity.RbacRoleEntity;

import java.util.List;
import java.util.Optional;

public interface IRbacRoleRepository {

    RbacRoleEntity save(Long accountId, RbacRoleEntity entity);

    Optional<RbacRoleEntity> findById(Long accountId, Long id);

    Optional<RbacRoleEntity> findByRoleCode(Long accountId, String roleCode);

    long countPage(Long accountId, String roleCode, String roleName, Boolean status);

    List<RbacRoleEntity> queryPage(Long accountId, String roleCode, String roleName, Boolean status, Integer offset, Integer pageSize);

    int updateById(Long accountId, RbacRoleEntity entity);

    int logicDeleteById(Long accountId, Long id);

    List<Long> queryExistingIds(Long accountId, List<Long> roleIds);

}
