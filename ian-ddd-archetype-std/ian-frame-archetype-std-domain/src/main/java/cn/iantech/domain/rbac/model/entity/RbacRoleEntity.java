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
public class RbacRoleEntity {

    private Long id;

    private Long accountId;

    private String roleCode;

    private String roleName;

    private String roleDesc;

    private Boolean status;

    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
