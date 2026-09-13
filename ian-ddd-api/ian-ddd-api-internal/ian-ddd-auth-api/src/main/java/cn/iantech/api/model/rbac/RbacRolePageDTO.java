package cn.iantech.api.model.rbac;

import cn.iantech.common.model.PageResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RbacRolePageDTO extends PageResponse<RbacRoleDTO> {

    private static final long serialVersionUID = 5258167462465842290L;

}
