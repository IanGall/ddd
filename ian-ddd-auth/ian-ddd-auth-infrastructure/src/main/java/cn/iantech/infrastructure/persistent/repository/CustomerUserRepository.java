package cn.iantech.infrastructure.persistent.repository;

import cn.iantech.common.exception.AppException;
import cn.iantech.domain.customer.infra.ICustomerUserRepository;
import cn.iantech.domain.customer.model.CustomerUserEntity;
import cn.iantech.infrastructure.persistent.dao.ICustomerUserDao;
import cn.iantech.infrastructure.persistent.po.CustomerUserPO;
import io.github.linpeilie.Converter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static cn.iantech.common.constant.Constants.ResponseCode.CONFLICT;

@Repository
public class CustomerUserRepository implements ICustomerUserRepository {
    private final ICustomerUserDao dao;
    private final Converter converter;

    public CustomerUserRepository(ICustomerUserDao dao, Converter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    /**
     * 主键由 {@code CustomerUserPO} 上的 {@code @IdGenerator} 注解在 insert 时填充。
     */
    @Override
    public CustomerUserEntity save(CustomerUserEntity entity) {
        CustomerUserPO po = converter.convert(entity, CustomerUserPO.class);
        // customer_user.avatar 为 NOT NULL DEFAULT ''，显式赋值避免 INSERT 绑定 null 触发约束冲突
        if (po.getAvatar() == null) {
            po.setAvatar("");
        }
        try {
            dao.insert(po);
        } catch (DuplicateKeyException exception) {
            // 并发注册时由 uk_customer_user_login_name 兜底；在此收口为业务异常，
            // 应用层（cases）无需感知具体的持久化技术栈
            throw new AppException(CONFLICT.getCode(), "登录账号已注册");
        }
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
}
