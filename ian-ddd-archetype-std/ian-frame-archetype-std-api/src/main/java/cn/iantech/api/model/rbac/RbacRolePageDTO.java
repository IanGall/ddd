package cn.iantech.api.model.rbac;

import cn.iantech.common.model.PageResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class RbacRolePageDTO extends PageResponse<RbacRoleDTO> {

    private static final long serialVersionUID = 5258167462465842290L;

}
