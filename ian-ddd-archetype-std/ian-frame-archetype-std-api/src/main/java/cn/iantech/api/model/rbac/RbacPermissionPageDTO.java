package cn.iantech.api.model.rbac;

import cn.iantech.common.model.PageResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class RbacPermissionPageDTO extends PageResponse<RbacPermissionDTO> {

    private static final long serialVersionUID = -3104169307245107984L;

}
