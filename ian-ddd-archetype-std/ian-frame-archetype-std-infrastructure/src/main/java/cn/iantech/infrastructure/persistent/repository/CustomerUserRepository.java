package cn.iantech.infrastructure.persistent.repository;

import cn.iantech.domain.customer.infra.ICustomerUserRepository;
import cn.iantech.domain.customer.model.CustomerUserEntity;
import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.infrastructure.persistent.dao.ICustomerUserDao;
import cn.iantech.infrastructure.persistent.po.CustomerUserPO;
import io.github.linpeilie.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CustomerUserRepository implements ICustomerUserRepository {
    private final ICustomerUserDao dao;
    private final Converter converter;
    private final GlobalIdGenerator globalIdGenerator;

    @Override
    public CustomerUserEntity save(CustomerUserEntity entity) {
        CustomerUserPO po = converter.convert(entity, CustomerUserPO.class);
        po.setId(globalIdGenerator.nextId());
        dao.insert(po);
        return converter.convert(po, CustomerUserEntity.class);
    }

    @Override
    public Optional<CustomerUserEntity> findByLoginName(String loginName) {
        return Optional.ofNullable(dao.selectByLoginName(loginName)).map(item -> converter.convert(item, CustomerUserEntity.class));
    }

    @Override
    public Optional<CustomerUserEntity> findById(Long id) {
        return Optional.ofNullable(id).map(dao::selectById).map(item -> converter.convert(item, CustomerUserEntity.class));
    }

    @Override
    public void updateLastLoginAt(Long id) {
        dao.updateLastLoginAt(id);
    }

    @Override
    public void updatePassword(Long id, String passwordHash) {
        dao.updatePassword(id, passwordHash);
    }
}
