package cn.iantech.api.model.rbac;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRbacRoleReq implements Serializable {

    private static final long serialVersionUID = -650121961508171272L;

    private String roleCode;

    private String roleName;

    private String roleDesc;

    private Boolean status;

}
