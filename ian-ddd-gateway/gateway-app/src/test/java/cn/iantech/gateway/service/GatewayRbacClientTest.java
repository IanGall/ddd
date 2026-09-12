package cn.iantech.gateway.service;

import cn.iantech.api.IRbacService;
import cn.iantech.api.model.rbac.QueryRbacRolePageReq;
import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayRbacClientTest {

    @Test
    void shouldTranslateRpcTimeoutAtClientBoundary() {
        IRbacService rbacService = mock(IRbacService.class);
        when(rbacService.queryRolePage(any(QueryRbacRolePageReq.class)))
                .thenThrow(new RpcException(RpcException.TIMEOUT_EXCEPTION, "timeout"));
        GatewayRbacClient client = new GatewayRbacClient();
        ReflectionTestUtils.setField(client, "rbacService", rbacService);

        AppException exception = assertThrows(AppException.class,
                () -> client.queryRolePage(QueryRbacRolePageReq.builder().pageNum(1).pageSize(20).build()));

        assertEquals(Constants.ResponseCode.RPC_TIMEOUT.getCode(), exception.getCode());
        assertEquals(RpcException.class, exception.getCause().getClass());
    }
}
