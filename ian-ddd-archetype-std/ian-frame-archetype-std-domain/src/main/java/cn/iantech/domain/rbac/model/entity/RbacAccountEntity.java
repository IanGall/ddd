package cn.iantech.domain.rbac.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 主账号根身份，同时承担账号隔离边界。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RbacAccountEntity {

    private Long id;
    private String username;
    private String passwordHash;
    private String displayName;
    private String email;
    private String mobile;
    private Boolean status;
    private Boolean deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
