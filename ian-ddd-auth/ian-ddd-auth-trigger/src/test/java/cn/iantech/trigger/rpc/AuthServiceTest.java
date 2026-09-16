package cn.iantech.trigger.rpc;

import cn.iantech.api.model.auth.AuthLoginReq;
import cn.iantech.api.model.auth.AuthSessionDTO;
import cn.iantech.api.model.auth.AuthSessionQueryReq;
import cn.iantech.api.model.auth.AuthTokenDTO;
import cn.iantech.api.model.auth.AuthValidateReq;
import cn.iantech.cases.auth.model.AuthCaseModels.IdentityResult;
import cn.iantech.cases.auth.model.AuthCaseModels.SessionResult;
import cn.iantech.cases.auth.model.AuthCaseModels.TokenResult;
import cn.iantech.cases.auth.service.AuthCaseService;
import cn.iantech.cases.channel.service.ChannelAuthCaseService;
import cn.iantech.trigger.convertor.AuthCommandConvertor;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Auth Dubbo 入站适配器的委托与 DTO 转换。
 */
class AuthServiceTest {

    private static final IdentityResult IDENTITY = new IdentityResult(
            1001L, 2002L, "alice", "ADMIN", "ADMIN_PRIMARY", "sub-1", "web", List.of("rbac:read"),
            "iantech", "opaque", "sess-1", 1001L, 3L, "rbac:*");

    private final AuthCaseService authCaseService = mock(AuthCaseService.class);
    private final ChannelAuthCaseService channelAuthCaseService = mock(ChannelAuthCaseService.class);
    private final AuthCommandConvertor convertor = Mappers.getMapper(AuthCommandConvertor.class);
    private final AuthService authService = new AuthService(authCaseService, channelAuthCaseService, convertor);

    @Test
    void shouldMapLoginResultToTokenDto() {
        when(authCaseService.login(any())).thenReturn(
                new TokenResult("access-1", "refresh-1", "Bearer", 900L, 7200L, "sess-1", IDENTITY));
        AuthLoginReq req = AuthLoginReq.builder().loginName("alice@1001.com").password("pwd")
                .clientType("WEB").build();

        AuthTokenDTO dto = authService.login(req);

        assertEquals("access-1", dto.getAccessToken());
        assertEquals("sess-1", dto.getSessionId());
        assertEquals(2002L, dto.getIdentity().getUserId());
        verify(authCaseService).login(any());
    }

    @Test
    void shouldMapSessionsToDtoList() {
        when(authCaseService.sessions("access-1")).thenReturn(List.of(
                new SessionResult("sess-1", "WEB", "device-1", "127.0.0.1", "junit",
                        Instant.parse("2026-09-13T00:00:00Z"), Instant.parse("2026-09-13T01:00:00Z"), true)));

        List<AuthSessionDTO> sessions = authService.sessions(
                AuthSessionQueryReq.builder().accessToken("access-1").build());

        assertEquals(1, sessions.size());
        assertEquals("sess-1", sessions.getFirst().getSessionId());
        assertEquals(true, sessions.getFirst().isCurrent());
    }

    @Test
    void shouldPassNullTokenThroughOnValidate() {
        when(authCaseService.validate(null)).thenReturn(IDENTITY);

        assertEquals("alice", authService.validate(new AuthValidateReq()).getUsername());
        verify(authCaseService).validate(null);
    }

    @Test
    void shouldTolerateNullRequestOnRevokeSession() {
        authService.revokeSession(null);

        verify(authCaseService).revokeSession(null, null);
    }
}
