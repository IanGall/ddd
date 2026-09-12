package cn.iantech.api.model.rbac;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RbacPermissionDTO implements Serializable {

    private static final long serialVersionUID = 9050901746490846090L;

    private Long id;

    private String permCode;

    private String permName;

    private Integer permType;

    private Long parentId;

    private String path;

    private String method;

    private Boolean status;

    private Boolean systemManaged;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
