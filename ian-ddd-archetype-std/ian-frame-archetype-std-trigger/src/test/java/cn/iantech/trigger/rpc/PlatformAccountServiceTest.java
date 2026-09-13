package cn.iantech.trigger.rpc;

import cn.iantech.api.model.rbac.PlatformCreateAccountReq;
import cn.iantech.api.model.rbac.RbacAccountDTO;
import cn.iantech.cases.rbac.model.RbacCaseCommands.AccountResult;
import cn.iantech.cases.rbac.model.RbacCaseCommands.CreateAccount;
import cn.iantech.cases.rbac.service.RbacCaseService;
import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 平台开户 RPC 入口必须在进入用例前完成平台凭据校验。
 */
class PlatformAccountServiceTest {

    private static final String TOKEN = "platform-admin-token";

    private final RbacCaseService rbacCaseService = mock(RbacCaseService.class);

    @Test
    void shouldRejectWrongPlatformTokenBeforeCallingCases() {
        PlatformAccountService service = new PlatformAccountService(rbacCaseService, TOKEN);
        PlatformCreateAccountReq req = PlatformCreateAccountReq.builder().platformToken("wrong").build();

        AppException exception = assertThrows(AppException.class, () -> service.createAccount(req));

        assertEquals(Constants.ResponseCode.ACCESS_DENIED.getCode(), exception.getCode());
        verify(rbacCaseService, never()).createAccount(any());
    }

    @Test
    void shouldRejectNullRequest() {
        PlatformAccountService service = new PlatformAccountService(rbacCaseService, TOKEN);

        assertThrows(AppException.class, () -> service.createAccount(null));
        verify(rbacCaseService, never()).createAccount(any());
    }

    @Test
    void shouldDelegateToCasesWithMappedCommandWhenTokenMatches() {
        when(rbacCaseService.createAccount(any()))
                .thenReturn(new AccountResult(1001L, "acme", "acme@1001.com"));
        PlatformAccountService service = new PlatformAccountService(rbacCaseService, TOKEN);
        PlatformCreateAccountReq req = PlatformCreateAccountReq.builder()
                .platformToken(TOKEN).username("acme").password("pwd").displayName("Acme")
                .email("acme@example.com").mobile("13800000000").build();

        RbacAccountDTO dto = service.createAccount(req);

        assertEquals(1001L, dto.getAccountId());
        assertEquals("acme", dto.getUsername());
        assertEquals("acme@1001.com", dto.getLoginName());

        ArgumentCaptor<CreateAccount> captor = ArgumentCaptor.forClass(CreateAccount.class);
        verify(rbacCaseService).createAccount(captor.capture());
        assertEquals("acme", captor.getValue().username());
        assertEquals("pwd", captor.getValue().password());
        assertEquals("Acme", captor.getValue().displayName());
        assertEquals("acme@example.com", captor.getValue().email());
        assertEquals("13800000000", captor.getValue().mobile());
    }

    @Test
    void shouldFailFastWhenConfiguredTokenBlank() {
        assertThrows(IllegalStateException.class, () -> new PlatformAccountService(rbacCaseService, " "));
    }
}
