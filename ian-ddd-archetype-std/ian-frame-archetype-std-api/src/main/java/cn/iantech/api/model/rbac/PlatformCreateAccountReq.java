package cn.iantech.api.model.rbac;

import lombok.*;

import java.io.Serializable;

/**
 * 平台创建主账号请求。该对象有意不生成 toString，避免平台凭据进入日志。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformCreateAccountReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private String platformToken;
    private String username;
    private String password;
    private String displayName;
    private String email;
    private String mobile;
}
