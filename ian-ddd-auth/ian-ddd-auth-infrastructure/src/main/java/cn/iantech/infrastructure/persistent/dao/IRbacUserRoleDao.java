package cn.iantech.infrastructure.persistent.dao;

import cn.iantech.infrastructure.persistent.po.RbacUserRolePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IRbacUserRoleDao {

    List<Long> selectRoleIdsByUserId(@Param("accountId") Long accountId, @Param("userId") Long userId);

    int deleteByUserIdAndRoleIds(@Param("accountId") Long accountId, @Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    int batchInsert(@Param("list") List<RbacUserRolePO> list);

    int deleteAllByUserId(@Param("accountId") Long accountId, @Param("userId") Long userId);

    int deleteAllByRoleId(@Param("accountId") Long accountId, @Param("roleId") Long roleId);

}
