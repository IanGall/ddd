package cn.iantech.infrastructure.persistent.repository;

import cn.iantech.common.exception.AppException;
import cn.iantech.domain.rbac.infra.IRbacAccountRepository;
import cn.iantech.domain.rbac.model.entity.RbacAccountEntity;
import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.id.GlobalIdGeneratorProvider;
import cn.iantech.infrastructure.id.AuthIdBusiness;
import cn.iantech.infrastructure.persistent.dao.IRbacAccountDao;
import cn.iantech.infrastructure.persistent.po.RbacAccountPO;
import io.github.linpeilie.Converter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static cn.iantech.common.constant.Constants.ResponseCode.INVALID_ARGUMENT;

@Repository
public class RbacAccountRepository implements IRbacAccountRepository {

    private final IRbacAccountDao accountDao;
    private final Converter converter;
    private final GlobalIdGenerator globalIdGenerator;

    public RbacAccountRepository(IRbacAccountDao accountDao, Converter converter,
                                GlobalIdGeneratorProvider idGeneratorProvider) {
        this.accountDao = accountDao;
        this.converter = converter;
        this.globalIdGenerator = idGeneratorProvider.forBusiness(AuthIdBusiness.RBAC_ACCOUNT.businessName());
    }

    @Override
    public RbacAccountEntity save(RbacAccountEntity entity) {
        RbacAccountPO item = converter.convert(entity, RbacAccountPO.class);
        item.setId(globalIdGenerator.nextId());
        try {
            accountDao.insert(item);
        } catch (DuplicateKeyException exception) {
            // 并发开户时由 username 唯一索引兜底，避免裸 DuplicateKeyException 直接 500
            throw new AppException(INVALID_ARGUMENT.getCode(), "账号名已存在");
        }
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
