package cn.iantech.domain.rbac.infra;

import cn.iantech.domain.rbac.model.entity.RbacAccountEntity;

import java.util.Optional;

public interface IRbacAccountRepository {

    RbacAccountEntity save(RbacAccountEntity entity);

    Optional<RbacAccountEntity> findById(Long id);

    Optional<RbacAccountEntity> findByUsername(Long id, String username);
}
