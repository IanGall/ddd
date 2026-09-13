package cn.iantech.domain.customer.infra;

import cn.iantech.domain.customer.model.CustomerUserEntity;

import java.util.Optional;

public interface ICustomerUserRepository {
    CustomerUserEntity save(CustomerUserEntity entity);

    Optional<CustomerUserEntity> findByLoginName(String loginName);

    Optional<CustomerUserEntity> findById(Long id);

    void updateLastLoginAt(Long id);

    void updatePassword(Long id, String passwordHash);
}
