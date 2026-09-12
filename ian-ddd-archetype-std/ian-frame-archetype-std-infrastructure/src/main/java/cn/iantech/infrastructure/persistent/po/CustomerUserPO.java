package cn.iantech.infrastructure.persistent.po;

import cn.iantech.common.model.BasePO;
import cn.iantech.domain.customer.model.CustomerUserEntity;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = CustomerUserEntity.class, reverseConvertGenerate = true)
public class CustomerUserPO extends BasePO {
    private String loginName;
    private String passwordHash;
    private String displayName;
    private String avatar;
    private Boolean status;
    // customer_user 存在的两个业务时间列，必须可映射，否则 MyBatis 查询会因缺少 setter 报错
    private LocalDateTime passwordChangedAt;
    private LocalDateTime lastLoginAt;
}
