package cn.iantech.test.rbac;

import cn.iantech.api.model.rbac.PlatformCreateAccountReq;
import cn.iantech.api.model.rbac.RbacAccountDTO;
import cn.iantech.cases.rbac.model.RbacCaseCommands.AccountResult;
import cn.iantech.cases.rbac.model.RbacCaseCommands.CreateAccount;
import cn.iantech.cases.rbac.service.RbacCaseService;
import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.trigger.rpc.PlatformAccountService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class PlatformAccountServiceTest {

    @Test
    void shouldRejectMissingProviderCredentialAtStartup() {
        RbacCaseService caseService = mock(RbacCaseService.class);

        Assertions.assertThrows(IllegalStateException.class, () ->
                new PlatformAccountService(caseService, " "));
    }

    @Test
    void shouldRejectInvalidPlatformCredential() {
        RbacCaseService caseService = mock(RbacCaseService.class);
        PlatformAccountService service = new PlatformAccountService(caseService, "expected-token");

        AppException exception = Assertions.assertThrows(AppException.class, () -> service.createAccount(
                PlatformCreateAccountReq.builder().platformToken("invalid-token").build()));

        Assertions.assertEquals(Constants.ResponseCode.ACCESS_DENIED.getCode(), exception.getCode());
    }

    @Test
    void shouldCreateAccountWithoutExposingCredentialInToString() {
        RbacCaseService caseService = mock(RbacCaseService.class);
        PlatformAccountService service = new PlatformAccountService(caseService, "expected-token");
        PlatformCreateAccountReq request = PlatformCreateAccountReq.builder()
                .platformToken("expected-token")
                .username("admin")
                .password("Pwd@0001")
                .build();
        CreateAccount command = new CreateAccount("admin", "Pwd@0001", null, null, null);
        when(caseService.createAccount(command)).thenReturn(new AccountResult(1L, "admin", "admin@1.com"));

        RbacAccountDTO actual = service.createAccount(request);

        Assertions.assertEquals(1L, actual.getAccountId());
        Assertions.assertEquals("admin", actual.getUsername());
        Assertions.assertEquals("admin@1.com", actual.getLoginName());
        Assertions.assertFalse(request.toString().contains("expected-token"));
        verify(caseService).createAccount(command);
    }
}
