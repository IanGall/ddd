package cn.iantech.api.model.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录请求及客户端元数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthLoginReq implements Serializable {
    private static final long serialVersionUID = 1L;
    private String loginName;
    private String password;
    private String clientType;
    private String deviceId;
    private String ipAddress;
    private String userAgent;
}
