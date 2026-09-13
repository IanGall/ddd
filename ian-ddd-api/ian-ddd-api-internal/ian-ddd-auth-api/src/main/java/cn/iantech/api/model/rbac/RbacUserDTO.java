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
public class RbacUserDTO implements Serializable {

    private static final long serialVersionUID = -6642387484330209454L;

    private Long id;

    private Long accountId;

    private String username;

    private String displayName;

    private String email;

    private String mobile;

    private Boolean status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
