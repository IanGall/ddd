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
public class RbacUserPageDTO extends PageResponse<RbacUserDTO> {

    private static final long serialVersionUID = 1799302472191382881L;

}
