package cn.iantech.infrastructure.persistent.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RbacRolePermissionPO {

    private Long id;

    private Long roleId;

    private Long accountId;

    private Long permissionId;

    private LocalDateTime createTime;

}
