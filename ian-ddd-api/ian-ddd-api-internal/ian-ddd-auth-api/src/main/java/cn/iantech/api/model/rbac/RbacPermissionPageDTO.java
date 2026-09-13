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
public class RbacPermissionPageDTO extends PageResponse<RbacPermissionDTO> {

    private static final long serialVersionUID = -3104169307245107984L;

}
