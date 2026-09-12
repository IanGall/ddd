package cn.iantech.domain.rbac.infra;

import cn.iantech.domain.rbac.model.entity.RbacUserEntity;

import java.util.List;
import java.util.Optional;

public interface IRbacUserRepository {

    RbacUserEntity save(Long accountId, RbacUserEntity entity);

    Optional<RbacUserEntity> findById(Long accountId, Long id);

    Optional<RbacUserEntity> findByUsername(Long accountId, String username);

    long countPage(Long accountId, String username, Boolean status);

    List<RbacUserEntity> queryPage(Long accountId, String username, Boolean status, Integer offset, Integer pageSize);

    int updateById(Long accountId, RbacUserEntity entity);

    int logicDeleteById(Long accountId, Long id);

}
