package cn.iantech.trigger.convertor;

import cn.iantech.api.model.auth.AuthIdentityDTO;
import cn.iantech.api.model.auth.AuthSessionDTO;
import cn.iantech.api.model.auth.AuthTokenDTO;
import cn.iantech.cases.auth.model.AuthCaseModels;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 认证领域结果到 RPC DTO 的字段映射契约。
 */
class AuthApiConverterTest {

    private static final AuthCaseModels.IdentityResult IDENTITY = new AuthCaseModels.IdentityResult(
            1001L, 2002L, "alice", "ADMIN", "ADMIN_PRIMARY", "sub-1", "web", List.of("rbac:read", "rbac:write"),
            "iantech", "opaque", "sess-1", 1001L, 3L, "rbac:*");

    @Test
    void shouldMapTokenResultIncludingNestedIdentity() {
        AuthCaseModels.TokenResult result = new AuthCaseModels.TokenResult(
                "access-1", "refresh-1", "Bearer", 900L, 7200L, "sess-1", IDENTITY);

        AuthTokenDTO dto = AuthApiConverter.toTokenDTO(result);

        assertEquals("access-1", dto.getAccessToken());
        assertEquals("refresh-1", dto.getRefreshToken());
        assertEquals("Bearer", dto.getTokenType());
        assertEquals(900L, dto.getExpiresIn());
        assertEquals(7200L, dto.getRefreshExpiresIn());
        assertEquals("sess-1", dto.getSessionId());
        assertIdentityMapped(dto.getIdentity());
    }

    @Test
    void shouldMapIdentityResultAllFields() {
        AuthIdentityDTO dto = AuthApiConverter.toIdentityDTO(IDENTITY);

        assertIdentityMapped(dto);
    }

    @Test
    void shouldMapSessionResultAndCurrentFlag() {
        Instant createdAt = Instant.parse("2026-09-13T00:00:00Z");
        Instant expiresAt = Instant.parse("2026-09-13T01:00:00Z");
        AuthCaseModels.SessionResult result = new AuthCaseModels.SessionResult(
                "sess-9", "WEB", "device-9", "127.0.0.1", "junit", createdAt, expiresAt, true);

        AuthSessionDTO dto = AuthApiConverter.toSessionDTO(result);

        assertEquals("sess-9", dto.getSessionId());
        assertEquals("WEB", dto.getClientType());
        assertEquals("device-9", dto.getDeviceId());
        assertEquals("127.0.0.1", dto.getIpAddress());
        assertEquals("junit", dto.getUserAgent());
        assertSame(createdAt, dto.getCreatedAt());
        assertSame(expiresAt, dto.getExpiresAt());
        assertEquals(true, dto.isCurrent());
    }

    private void assertIdentityMapped(AuthIdentityDTO dto) {
        assertEquals(1001L, dto.getAccountId());
        assertEquals(2002L, dto.getUserId());
        assertEquals("alice", dto.getUsername());
        assertEquals("ADMIN", dto.getUserType());
        assertEquals("ADMIN_PRIMARY", dto.getSubjectType());
        assertEquals("sub-1", dto.getSubjectId());
        assertEquals("web", dto.getClientId());
        assertEquals(List.of("rbac:read", "rbac:write"), dto.getScopes());
        assertEquals("iantech", dto.getIssuer());
        assertEquals("opaque", dto.getTokenKind());
        assertEquals("sess-1", dto.getSessionId());
        assertEquals(1001L, dto.getOwnerAccountId());
        assertEquals(3L, dto.getCredentialVersion());
        assertEquals("rbac:*", dto.getAuthorizedScope());
    }
}
