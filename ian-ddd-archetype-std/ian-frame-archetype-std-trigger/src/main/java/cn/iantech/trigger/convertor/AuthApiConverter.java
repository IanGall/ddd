package cn.iantech.trigger.convertor;

import cn.iantech.api.model.auth.AuthIdentityDTO;
import cn.iantech.api.model.auth.AuthSessionDTO;
import cn.iantech.api.model.auth.AuthTokenDTO;
import cn.iantech.cases.auth.model.AuthCaseModels.IdentityResult;
import cn.iantech.cases.auth.model.AuthCaseModels.SessionResult;
import cn.iantech.cases.auth.model.AuthCaseModels.TokenResult;

/**
 * 认证领域结果与 RPC DTO 的边界转换器。
 */
public final class AuthApiConverter {

    private AuthApiConverter() {
    }

    public static AuthTokenDTO toTokenDTO(TokenResult result) {
        return AuthTokenDTO.builder()
                .accessToken(result.accessToken())
                .refreshToken(result.refreshToken())
                .tokenType(result.tokenType())
                .expiresIn(result.expiresIn())
                .refreshExpiresIn(result.refreshExpiresIn())
                .sessionId(result.sessionId())
                .identity(toIdentityDTO(result.identity()))
                .build();
    }

    public static AuthIdentityDTO toIdentityDTO(IdentityResult result) {
        return AuthIdentityDTO.builder()
                .accountId(result.accountId())
                .userId(result.userId())
                .username(result.username())
                .userType(result.userType())
                .subjectType(result.subjectType())
                .subjectId(result.subjectId())
                .clientId(result.clientId())
                .scopes(result.scopes())
                .issuer(result.issuer())
                .tokenKind(result.tokenKind())
                .sessionId(result.sessionId())
                .ownerAccountId(result.ownerAccountId())
                .credentialVersion(result.credentialVersion())
                .authorizedScope(result.authorizedScope())
                .build();
    }

    public static AuthSessionDTO toSessionDTO(SessionResult result) {
        return AuthSessionDTO.builder()
                .sessionId(result.sessionId())
                .clientType(result.clientType())
                .deviceId(result.deviceId())
                .ipAddress(result.ipAddress())
                .userAgent(result.userAgent())
                .createdAt(result.createdAt())
                .expiresAt(result.expiresAt())
                .current(result.current())
                .build();
    }
}
