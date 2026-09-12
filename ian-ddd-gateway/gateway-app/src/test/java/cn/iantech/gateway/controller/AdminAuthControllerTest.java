package cn.iantech.gateway.controller;

import cn.iantech.api.model.auth.AuthIdentityDTO;
import cn.iantech.api.model.auth.AuthRefreshReq;
import cn.iantech.api.model.auth.AuthSubjectTypes;
import cn.iantech.api.model.auth.AuthTokenDTO;
import cn.iantech.common.exception.AppException;
import cn.iantech.gateway.model.AuthWebModels;
import cn.iantech.gateway.service.GatewayAuthClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminAuthControllerTest {

    @Test
    void shouldFixAdminSubjectWhenRefreshing() {
        GatewayAuthClient authClient = mock(GatewayAuthClient.class);
        when(authClient.refresh(any())).thenReturn(token("ADMIN_PRIMARY"));
        AdminAuthController controller = new AdminAuthController(authClient);

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
        AdminAuthController controller = new AdminAuthController(authClient);

        assertThrows(AppException.class, () -> controller.refresh(
                new AuthWebModels.RefreshRequest("refresh-token", "web", "device"),
                new MockHttpServletRequest()));
    }

    private AuthTokenDTO token(String subjectType) {
        AuthIdentityDTO identity = AuthIdentityDTO.builder().subjectType(subjectType).userId(1L).build();
        return AuthTokenDTO.builder().accessToken("access").refreshToken("refresh").tokenType("Bearer")
                .sessionId("session").identity(identity).build();
    }
}
