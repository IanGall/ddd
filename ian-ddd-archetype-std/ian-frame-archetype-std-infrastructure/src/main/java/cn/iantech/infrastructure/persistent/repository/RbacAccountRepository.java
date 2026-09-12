package cn.iantech.infrastructure.persistent.repository;

import cn.iantech.domain.rbac.infra.IRbacAccountRepository;
import cn.iantech.domain.rbac.model.entity.RbacAccountEntity;
import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.infrastructure.persistent.dao.IRbacAccountDao;
import cn.iantech.infrastructure.persistent.po.RbacAccountPO;
import io.github.linpeilie.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RbacAccountRepository implements IRbacAccountRepository {

    private final IRbacAccountDao accountDao;
    private final Converter converter;
    private final GlobalIdGenerator globalIdGenerator;

    @Override
    public RbacAccountEntity save(RbacAccountEntity entity) {
        RbacAccountPO item = converter.convert(entity, RbacAccountPO.class);
        item.setId(globalIdGenerator.nextId());
        accountDao.insert(item);
        return converter.convert(item, RbacAccountEntity.class);
    }

    @Override
    public Optional<RbacAccountEntity> findById(Long id) {
        return Optional.ofNullable(accountDao.selectById(id))
                .map(item -> converter.convert(item, RbacAccountEntity.class));
    }

    @Override
    public Optional<RbacAccountEntity> findByUsername(Long id, String username) {
        return Optional.ofNullable(accountDao.selectByUsername(id, username))
                .map(item -> converter.convert(item, RbacAccountEntity.class));
    }
}
