package cn.iantech.gateway.controller;

import cn.iantech.api.model.rbac.PlatformCreateAccountReq;
import cn.iantech.api.model.rbac.RbacAccountDTO;
import cn.iantech.common.model.Response;
import cn.iantech.gateway.service.GatewayRbacClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import static cn.iantech.gateway.model.GatewayResponses.success;

@RestController
@RequestMapping("/api/admin/platform/accounts")
public class PlatformAccountController {

    private final GatewayRbacClient platformAccountClient;

    public PlatformAccountController(GatewayRbacClient platformAccountClient) {
        this.platformAccountClient = platformAccountClient;
    }

    @PostMapping
    public Response<RbacAccountDTO> createAccount(
            @RequestHeader("X-Platform-Token") String platformToken,
            @Valid @RequestBody CreateAccountRequest request) {
        return success(platformAccountClient.createAccount(PlatformCreateAccountReq.builder()
                .platformToken(platformToken)
                .username(request.username())
                .password(request.password())
                .displayName(request.displayName())
                .email(request.email())
                .mobile(request.mobile())
                .build()));
    }

    public record CreateAccountRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            @Size(max = 128) String displayName,
            @Email @Size(max = 128) String email,
            @Size(max = 32) String mobile) {
    }
}
