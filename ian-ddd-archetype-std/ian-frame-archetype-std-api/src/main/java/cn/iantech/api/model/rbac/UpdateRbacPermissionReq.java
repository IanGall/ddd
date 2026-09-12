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
public class UpdateRbacPermissionReq implements Serializable {

    private static final long serialVersionUID = -203808635560947550L;

    private Long id;

    private String permName;

    private Integer permType;

    private Long parentId;

    private String path;

    private String method;

    private Boolean status;

}
