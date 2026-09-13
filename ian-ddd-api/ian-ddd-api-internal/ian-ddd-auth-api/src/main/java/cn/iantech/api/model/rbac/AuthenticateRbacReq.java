package cn.iantech.api.model.rbac;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 主账号或子账号登录请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticateRbacReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private String loginName;

    private String password;
}
