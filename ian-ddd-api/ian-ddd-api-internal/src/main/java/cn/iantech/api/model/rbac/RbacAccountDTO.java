package cn.iantech.api.model.rbac;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 主账号创建结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RbacAccountDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long accountId;
    private String username;
    private String loginName;
}
