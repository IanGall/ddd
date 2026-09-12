package cn.iantech.gateway.controller;

import cn.iantech.common.model.Response;
import cn.iantech.gateway.model.GatewayStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iantech.gateway.model.GatewayResponses.success;

/**
 * 管理端网关状态接口。
 */
@RestController
@RequestMapping("/api/admin/status")
public class GatewayStatusController {

    private final String applicationName;

    public GatewayStatusController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping
    public Response<GatewayStatus> status() {
        return success(new GatewayStatus(applicationName, "UP"));
    }
}
