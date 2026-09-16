package cn.iantech.infrastructure.persistent.repository;

import cn.iantech.common.exception.AppException;
import cn.iantech.domain.rbac.infra.IRbacUserRepository;
import cn.iantech.domain.rbac.model.entity.RbacUserEntity;
import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.id.GlobalIdGeneratorProvider;
import cn.iantech.infrastructure.id.AuthIdBusiness;
import cn.iantech.infrastructure.persistent.dao.IRbacUserDao;
import cn.iantech.infrastructure.persistent.po.RbacUserPO;
import io.github.linpeilie.Converter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static cn.iantech.common.constant.Constants.ResponseCode.INVALID_ARGUMENT;

@Repository
public class RbacUserRepository implements IRbacUserRepository {

    private final IRbacUserDao rbacUserDao;
    private final Converter converter;
    private final GlobalIdGenerator globalIdGenerator;

    public RbacUserRepository(IRbacUserDao rbacUserDao, Converter converter,
                             GlobalIdGeneratorProvider idGeneratorProvider) {
        this.rbacUserDao = rbacUserDao;
        this.converter = converter;
        this.globalIdGenerator = idGeneratorProvider.forBusiness(AuthIdBusiness.IDENTITY.businessName());
    }

    @Override
    public RbacUserEntity save(Long accountId, RbacUserEntity entity) {
        RbacUserPO rbacUserPO = converter.convert(entity, RbacUserPO.class);
        rbacUserPO.setAccountId(accountId);
        rbacUserPO.setId(globalIdGenerator.nextId());
        LocalDateTime now = LocalDateTime.now();
        rbacUserPO.setCreateTime(now);
        rbacUserPO.setUpdateTime(now);
        try {
            rbacUserDao.insert(rbacUserPO);
        } catch (DuplicateKeyException exception) {
            // 并发注册唯一索引兜底，避免裸 DuplicateKeyException 直接 500
            throw new AppException(INVALID_ARGUMENT.getCode(), "用户名已存在");
        }
        return converter.convert(rbacUserPO, RbacUserEntity.class);
    }

    @Override
    public Optional<RbacUserEntity> findById(Long accountId, Long id) {
        return Optional.ofNullable(rbacUserDao.selectById(accountId, id))
                .map(po -> converter.convert(po, RbacUserEntity.class));
    }

    @Override
    public Optional<RbacUserEntity> findByUsername(Long accountId, String username) {
        return Optional.ofNullable(rbacUserDao.selectByUsername(accountId, username))
                .map(po -> converter.convert(po, RbacUserEntity.class));
    }

    @Override
    public long countPage(Long accountId, String username, Boolean status) {
        return rbacUserDao.selectPageCount(accountId, username, status);
    }

    @Override
    public List<RbacUserEntity> queryPage(Long accountId, String username, Boolean status, Integer offset, Integer pageSize) {
        List<RbacUserPO> poList = Optional.ofNullable(rbacUserDao.selectPage(accountId, username, status, offset, pageSize))
                .orElse(List.of());
        return poList.stream()
                .map(po -> converter.convert(po, RbacUserEntity.class))
                .toList();
    }

    @Override
    public int updateById(Long accountId, RbacUserEntity entity) {
        RbacUserPO po = converter.convert(entity, RbacUserPO.class);
        po.setAccountId(accountId);
        return rbacUserDao.updateById(accountId, po);
    }

    @Override
    public int logicDeleteById(Long accountId, Long id) {
        return rbacUserDao.logicDeleteById(accountId, id);
    }

}
