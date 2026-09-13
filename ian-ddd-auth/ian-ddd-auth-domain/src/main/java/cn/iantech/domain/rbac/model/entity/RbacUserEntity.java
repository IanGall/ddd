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
public class RbacUserEntity {

    private Long id;

    private Long accountId;

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
