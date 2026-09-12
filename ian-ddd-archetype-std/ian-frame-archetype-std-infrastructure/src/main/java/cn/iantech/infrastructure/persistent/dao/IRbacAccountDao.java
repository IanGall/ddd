package cn.iantech.infrastructure.persistent.dao;

import cn.iantech.infrastructure.persistent.po.RbacAccountPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IRbacAccountDao {

    int insert(RbacAccountPO item);

    RbacAccountPO selectById(@Param("id") Long id);

    RbacAccountPO selectByUsername(@Param("id") Long id, @Param("username") String username);
}
