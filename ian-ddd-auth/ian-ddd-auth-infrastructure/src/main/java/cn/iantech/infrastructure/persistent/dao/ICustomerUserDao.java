package cn.iantech.infrastructure.persistent.dao;

import cn.iantech.infrastructure.persistent.po.CustomerUserPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ICustomerUserDao {
    int insert(CustomerUserPO item);

    CustomerUserPO selectByLoginName(@Param("loginName") String loginName);

    CustomerUserPO selectById(@Param("id") Long id);

    int updateLastLoginAt(@Param("id") Long id);
}
