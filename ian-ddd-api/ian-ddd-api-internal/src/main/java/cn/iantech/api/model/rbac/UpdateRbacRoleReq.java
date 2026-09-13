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
public class UpdateRbacRoleReq implements Serializable {

    private static final long serialVersionUID = 6675003852949777314L;

    private Long id;

    private String roleCode;

    private String roleName;

    private String roleDesc;

    private Boolean status;

}
