package cn.iantech.infrastructure.persistent.po;

import cn.iantech.common.model.BasePO;
import cn.iantech.domain.rbac.model.entity.RbacUserEntity;
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
@AutoMapper(target = RbacUserEntity.class, reverseConvertGenerate = true)
public class RbacUserPO extends BasePO {

    private String username;

    private Long accountId;

    private String passwordHash;

    private String displayName;

    private String email;

    private String mobile;

    private Boolean status;

}
