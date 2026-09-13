package cn.iantech.api.model.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Refresh Token 轮换请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthRefreshReq implements Serializable {
    private static final long serialVersionUID = 1L;
    private String refreshToken;
    private String expectedSubjectType;
    private String clientType;
    private String deviceId;
    private String ipAddress;
    private String userAgent;
}
