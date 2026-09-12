package cn.iantech.infrastructure.persistent.po;

import cn.iantech.common.model.BasePO;
import cn.iantech.domain.customer.model.CustomerUserEntity;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

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
}
