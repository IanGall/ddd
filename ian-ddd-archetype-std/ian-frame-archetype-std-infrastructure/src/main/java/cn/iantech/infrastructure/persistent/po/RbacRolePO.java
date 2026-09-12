package cn.iantech.infrastructure.persistent.po;

import cn.iantech.common.model.BasePO;
import cn.iantech.domain.rbac.model.entity.RbacRoleEntity;
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
@AutoMapper(target = RbacRoleEntity.class, reverseConvertGenerate = true)
public class RbacRolePO extends BasePO {

    private String roleCode;

    private Long accountId;

    private String roleName;

    private String roleDesc;

    private Boolean status;

}
