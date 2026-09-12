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
public class RbacRoleDTO implements Serializable {

    private static final long serialVersionUID = -3847931026334826773L;

    private Long id;

    private String roleCode;

    private String roleName;

    private String roleDesc;

    private Boolean status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
