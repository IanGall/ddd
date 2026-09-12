package cn.iantech.gateway.controller;

import cn.iantech.api.model.rbac.PlatformCreateAccountReq;
import cn.iantech.api.model.rbac.RbacAccountDTO;
import cn.iantech.common.model.Response;
import cn.iantech.gateway.service.GatewayRbacClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlatformAccountControllerTest {

    @Test
    void shouldForwardPlatformTokenAndAccountFieldsToProvider() {
        GatewayRbacClient client = mock(GatewayRbacClient.class);
        RbacAccountDTO account = RbacAccountDTO.builder().accountId(1001L).username("root").build();
        when(client.createAccount(any())).thenReturn(account);
        PlatformAccountController controller = new PlatformAccountController(client);

        Response<RbacAccountDTO> response = controller.createAccount("provider-owned-token",
                new PlatformAccountController.CreateAccountRequest(
                        "root", "password-123", "根账号", "root@example.com", "13800000000"));

        ArgumentCaptor<PlatformCreateAccountReq> captor = ArgumentCaptor.forClass(PlatformCreateAccountReq.class);
        verify(client).createAccount(captor.capture());
        PlatformCreateAccountReq request = captor.getValue();
        assertEquals("provider-owned-token", request.getPlatformToken());
        assertEquals("root", request.getUsername());
        assertEquals("password-123", request.getPassword());
        assertEquals(1001L, response.getData().getAccountId());
    }
}
