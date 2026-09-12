package cn.iantech.gateway.controller;

import cn.iantech.common.constant.Constants;
import cn.iantech.common.model.Response;
import cn.iantech.gateway.model.GatewayStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewayStatusControllerTest {

    @Test
    void shouldReturnCurrentGatewayStatus() {
        Response<GatewayStatus> response = new GatewayStatusController("test-gateway").status();

        assertEquals(Constants.ResponseCode.SUCCESS.getCode(), response.getCode());
        assertEquals("test-gateway", response.getData().application());
        assertEquals("UP", response.getData().status());
    }
}
