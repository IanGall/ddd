package cn.iantech.api.model.rbac;

import cn.iantech.common.model.PageRequest;
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
public class QueryRbacRolePageReq extends PageRequest {

    private static final long serialVersionUID = -4787529108979838307L;

    private String roleCode;

    private String roleName;

    private Boolean status;

}
