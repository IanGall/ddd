package cn.iantech.infrastructure.persistent.dao;

import cn.iantech.infrastructure.persistent.po.UserOrderPO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface IUserOrderDao {

    /**
     * 插入订单。主键由 {@code UserOrderPO} 上的 {@code @IdGenerator} 注解在 insert 时填充，
     * 因此这里返回受影响行数而不是依赖数据库回填主键。
     */
    int insert(UserOrderPO userOrderPO);

    void updateOrderStatusByUserId(String userId);

    List<UserOrderPO> selectByUserId(String userId);

}
