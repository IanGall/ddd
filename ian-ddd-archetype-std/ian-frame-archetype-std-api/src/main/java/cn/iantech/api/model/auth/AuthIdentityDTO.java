package cn.iantech.api.model.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 当前认证会话对应的最小可信身份。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthIdentityDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long accountId;
    private Long userId;
    private String username;
    private String userType;
    private String subjectType;
    private String subjectId;
    private String clientId;
    private List<String> scopes;
    private String issuer;
    private String tokenKind;
    private String sessionId;
    private Long ownerAccountId;
    private Long credentialVersion;
    private String authorizedScope;
}
