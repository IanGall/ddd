package cn.iantech.gateway.service;

import cn.iantech.api.IAuthService;
import cn.iantech.api.model.auth.AuthLoginReq;
import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayAuthClientTest {

    private IAuthService authService;
    private GatewayAuthClient client;

    @BeforeEach
    void setUp() {
        authService = mock(IAuthService.class);
        client = new GatewayAuthClient();
        ReflectionTestUtils.setField(client, "authService", authService);
    }

    @Test
    void shouldPropagateAppExceptionWithoutChangingIdentity() {
        AppException expected = new AppException(Constants.ResponseCode.AUTH_REQUIRED.getCode(), "令牌无效");
        when(authService.login(ArgumentMatchers.any(AuthLoginReq.class))).thenThrow(expected);

        AppException actual = assertThrows(AppException.class,
                () -> client.login(AuthLoginReq.builder().loginName("user").password("password").build()));

        assertSame(expected, actual);
    }

    @Test
    void shouldNormalizeRpcTimeoutToGatewayTimeout() {
        when(authService.login(ArgumentMatchers.any(AuthLoginReq.class)))
                .thenThrow(new RpcException(RpcException.TIMEOUT_EXCEPTION, "timeout"));

        AppException actual = assertThrows(AppException.class,
                () -> client.login(AuthLoginReq.builder().loginName("user").password("password").build()));

        assertEquals(Constants.ResponseCode.RPC_TIMEOUT.getCode(), actual.getCode());
        assertEquals(Constants.ResponseCode.RPC_TIMEOUT.getInfo(), actual.getInfo());
        assertEquals(RpcException.class, actual.getCause().getClass());
    }

    @Test
    void shouldNormalizeUnexpectedRuntimeExceptionToAuthUnavailable() {
        when(authService.login(ArgumentMatchers.any(AuthLoginReq.class)))
                .thenThrow(new IllegalStateException("unexpected"));

        AppException actual = assertThrows(AppException.class,
                () -> client.login(AuthLoginReq.builder().loginName("user").password("password").build()));

        assertEquals(Constants.ResponseCode.AUTH_UNAVAILABLE.getCode(), actual.getCode());
    }

    @Test
    void shouldPropagateAppExceptionFromRuntimeCauseChain() {
        AppException expected = new AppException(Constants.ResponseCode.AUTH_REQUIRED.getCode(), "令牌无效");
        when(authService.login(ArgumentMatchers.any(AuthLoginReq.class)))
                .thenThrow(new IllegalStateException("rpc wrapper", expected));

        AppException actual = assertThrows(AppException.class,
                () -> client.login(AuthLoginReq.builder().loginName("user").password("password").build()));

        assertSame(expected, actual);
    }
}
