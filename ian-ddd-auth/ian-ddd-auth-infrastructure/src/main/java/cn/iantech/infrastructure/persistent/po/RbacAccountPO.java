package cn.iantech.infrastructure.persistent.po;

import cn.iantech.common.model.BasePO;
import cn.iantech.domain.rbac.model.entity.RbacAccountEntity;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = RbacAccountEntity.class, reverseConvertGenerate = true)
public class RbacAccountPO extends BasePO {

    private String username;
    private String passwordHash;
    private String displayName;
    private String email;
    private String mobile;
    private Boolean status;
}
