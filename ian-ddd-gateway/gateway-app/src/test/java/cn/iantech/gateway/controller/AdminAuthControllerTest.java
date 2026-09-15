package cn.iantech.gateway.controller;

import cn.iantech.api.model.auth.AuthIdentityDTO;
import cn.iantech.api.model.auth.AuthRefreshReq;
import cn.iantech.api.model.auth.AuthSubjectTypes;
import cn.iantech.api.model.auth.AuthTokenDTO;
import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.common.model.Response;
import cn.iantech.gateway.model.AuthWebModels;
import cn.iantech.gateway.core.service.GatewayAuthClient;
import cn.iantech.gateway.service.GatewayRbacClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminAuthControllerTest {

    @Test
    void shouldFixAdminSubjectWhenRefreshing() {
        GatewayAuthClient authClient = mock(GatewayAuthClient.class);
        when(authClient.refresh(any())).thenReturn(token("ADMIN_PRIMARY"));
        AdminAuthController controller = new AdminAuthController(authClient, mock(GatewayRbacClient.class));

        controller.refresh(new AuthWebModels.RefreshRequest("refresh-token", "web", "device"),
                new MockHttpServletRequest());

        ArgumentCaptor<AuthRefreshReq> captor = ArgumentCaptor.forClass(AuthRefreshReq.class);
        verify(authClient).refresh(captor.capture());
        assertEquals(AuthSubjectTypes.ADMIN, captor.getValue().getExpectedSubjectType());
    }

    @Test
    void shouldRejectCustomerIdentityReturnedToAdminEndpoint() {
        GatewayAuthClient authClient = mock(GatewayAuthClient.class);
        when(authClient.refresh(any())).thenReturn(token("CUSTOMER"));
        AdminAuthController controller = new AdminAuthController(authClient, mock(GatewayRbacClient.class));

        assertThrows(AppException.class, () -> controller.refresh(
                new AuthWebModels.RefreshRequest("refresh-token", "web", "device"),
                new MockHttpServletRequest()));
    }

    @Test
    void shouldReturnOwnPermissionCodesAsSuccessPayload() {
        GatewayRbacClient rbacClient = mock(GatewayRbacClient.class);
        when(rbacClient.queryOwnPermissionCodes()).thenReturn(List.of("rbac:role:read", "rbac:user:read"));
        AdminAuthController controller = new AdminAuthController(mock(GatewayAuthClient.class), rbacClient);

        Response<List<String>> response = controller.permissions();

        assertEquals(Constants.ResponseCode.SUCCESS.getCode(), response.getCode());
        assertEquals(List.of("rbac:role:read", "rbac:user:read"), response.getData());
    }

    private AuthTokenDTO token(String subjectType) {
        AuthIdentityDTO identity = AuthIdentityDTO.builder().subjectType(subjectType).userId(1L).build();
        return AuthTokenDTO.builder().accessToken("access").refreshToken("refresh").tokenType("Bearer")
                .sessionId("session").identity(identity).build();
    }
}
