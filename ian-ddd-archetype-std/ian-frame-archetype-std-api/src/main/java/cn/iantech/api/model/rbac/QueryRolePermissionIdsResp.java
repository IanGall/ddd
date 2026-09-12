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
public class QueryRolePermissionIdsResp implements Serializable {

    private static final long serialVersionUID = -5037743782862491731L;

    private Long roleId;

    private List<Long> permissionIds;

}
