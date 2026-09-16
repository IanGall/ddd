package cn.iantech.mysql.itest;

import org.apache.ibatis.annotations.Param;

public interface SampleOrderMapper {

    int insert(SampleOrderPo po);

    String findNameById(@Param("id") Long id);
}
