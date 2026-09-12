package cn.iantech.gateway.service;

import cn.iantech.api.IAuthService;
import cn.iantech.api.model.channel.ChannelSignatureVerifyReq;
import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayAuthChannelClientTest {

    private IAuthService service;
    private GatewayAuthClient client;

    @BeforeEach
    void setUp() {
        service = mock(IAuthService.class);
        client = new GatewayAuthClient();
        ReflectionTestUtils.setField(client, "authService", service);
    }

    @Test
    void shouldPreserveAuthenticationFailure() {
        AppException expected = new AppException(Constants.ResponseCode.AUTH_REQUIRED.getCode(), "渠道认证失败");
        when(service.authenticateChannel(any())).thenThrow(expected);

        AppException actual = assertThrows(AppException.class,
                () -> client.authenticateChannel(new ChannelSignatureVerifyReq()));

        assertSame(expected, actual);
    }

    @Test
    void shouldMapRpcTimeout() {
        when(service.authenticateChannel(any())).thenThrow(new RpcException(RpcException.TIMEOUT_EXCEPTION, "timeout"));

        AppException actual = assertThrows(AppException.class,
                () -> client.authenticateChannel(new ChannelSignatureVerifyReq()));

        assertEquals(Constants.ResponseCode.RPC_TIMEOUT.getCode(), actual.getCode());
    }
}
