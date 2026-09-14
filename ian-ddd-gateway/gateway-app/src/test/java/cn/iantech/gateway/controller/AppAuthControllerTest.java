package cn.iantech.gateway.controller;

import cn.iantech.api.model.auth.AuthIdentityDTO;
import cn.iantech.api.model.auth.AuthRefreshReq;
import cn.iantech.api.model.auth.AuthSubjectTypes;
import cn.iantech.api.model.auth.AuthTokenDTO;
import cn.iantech.api.model.customer.CustomerRegisterReq;
import cn.iantech.api.model.customer.CustomerUserDTO;
import cn.iantech.common.exception.AppException;
import cn.iantech.gateway.model.AuthWebModels;
import cn.iantech.gateway.core.service.GatewayAuthClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppAuthControllerTest {

    @Test
    void shouldFixCustomerSubjectWhenRefreshing() {
        GatewayAuthClient authClient = mock(GatewayAuthClient.class);
        when(authClient.refresh(any())).thenReturn(token());
        AppAuthController controller = new AppAuthController(authClient);

        controller.refresh(new AuthWebModels.RefreshRequest("refresh-token", "mobile", "device"),
                new MockHttpServletRequest());

        ArgumentCaptor<AuthRefreshReq> captor = ArgumentCaptor.forClass(AuthRefreshReq.class);
        verify(authClient).refresh(captor.capture());
        assertEquals(AuthSubjectTypes.CUSTOMER, captor.getValue().getExpectedSubjectType());
    }

    @Test
    void shouldRejectAdminIdentityReturnedToAppEndpoint() {
        GatewayAuthClient authClient = mock(GatewayAuthClient.class);
        when(authClient.refresh(any())).thenReturn(token("ADMIN_PRIMARY"));
        AppAuthController controller = new AppAuthController(authClient);

        assertThrows(AppException.class, () -> controller.refresh(
                new AuthWebModels.RefreshRequest("refresh-token", "mobile", "device"),
                new MockHttpServletRequest()));
    }

    @Test
    void shouldFillClientIpFromServletRequestOnRegister() {
        GatewayAuthClient authClient = mock(GatewayAuthClient.class);
        when(authClient.register(any())).thenReturn(new CustomerUserDTO());
        AppAuthController controller = new AppAuthController(authClient);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("10.0.0.1");

        controller.register(new AuthWebModels.RegisterRequest("13800000000", "pwd-1234", "C 端用户"),
                servletRequest);

        ArgumentCaptor<CustomerRegisterReq> captor = ArgumentCaptor.forClass(CustomerRegisterReq.class);
        verify(authClient).register(captor.capture());
        assertEquals("13800000000", captor.getValue().getLoginName());
        assertEquals("C 端用户", captor.getValue().getDisplayName());
        assertEquals("10.0.0.1", captor.getValue().getIpAddress(),
                "客户端 IP 必须取自连接地址：认证服务据此做注册风控，且请求体不含该字段");
    }

    private AuthTokenDTO token() {
        return token("CUSTOMER");
    }

    private AuthTokenDTO token(String subjectType) {
        AuthIdentityDTO identity = AuthIdentityDTO.builder().subjectType(subjectType).userId(1L).build();
        return AuthTokenDTO.builder().accessToken("access").refreshToken("refresh").tokenType("Bearer")
                .sessionId("session").identity(identity).build();
    }
}
