package cn.iantech.api.model.rbac;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplaceRolePermissionsReq implements Serializable {

    private static final long serialVersionUID = 304616726273848219L;

    private Long roleId;

    private List<Long> permissionIds;

}
