package cn.iantech.infrastructure.persistent.po;

import cn.iantech.common.model.BasePO;
import cn.iantech.domain.rbac.model.entity.RbacPermissionEntity;
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
@AutoMapper(target = RbacPermissionEntity.class, reverseConvertGenerate = true)
public class RbacPermissionPO extends BasePO {

    private String permCode;

    private Long accountId;

    private String permName;

    private Integer permType;

    private Long parentId;

    private String path;

    private String method;

    private Boolean status;

    private Boolean systemManaged;

}
