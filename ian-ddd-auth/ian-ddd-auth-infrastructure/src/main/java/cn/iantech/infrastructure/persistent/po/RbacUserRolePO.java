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
public class RbacUserRolePO {

    private Long id;

    private Long userId;

    private Long accountId;

    private Long roleId;

    private LocalDateTime createTime;

}
