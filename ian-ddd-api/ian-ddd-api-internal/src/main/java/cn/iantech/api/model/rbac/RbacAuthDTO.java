package cn.iantech.api.model.rbac;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 认证成功后的最小可信身份。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RbacAuthDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private Long accountId;
    private String username;
    private String userType;
    private List<String> roleCodes;
    private List<String> permissionCodes;
}
