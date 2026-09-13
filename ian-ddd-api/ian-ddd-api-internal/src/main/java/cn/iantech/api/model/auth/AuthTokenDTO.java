package cn.iantech.api.model.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录或刷新返回的双令牌结果，令牌本身为不可解析的随机字符串。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthTokenDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private long refreshExpiresIn;
    private String sessionId;
    private AuthIdentityDTO identity;
}
