package cn.iantech.domain.rbac.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RbacPermissionEntity {

    private Long id;

    private Long accountId;

    private String permCode;

    private String permName;

    private Integer permType;

    private Long parentId;

    private String path;

    private String method;

    private Boolean status;

    private Boolean systemManaged;

    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
