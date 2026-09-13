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
public class CreateRbacPermissionReq implements Serializable {

    private static final long serialVersionUID = -7865045197280689546L;

    private String permCode;

    private String permName;

    private Integer permType;

    private Long parentId;

    private String path;

    private String method;

    private Boolean status;

}
