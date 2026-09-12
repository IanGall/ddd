package cn.iantech.gateway.controller;

import cn.iantech.api.model.channel.*;
import cn.iantech.common.model.Response;
import cn.iantech.gateway.model.ChannelCredentialWebRequests;
import cn.iantech.gateway.service.GatewayChannelCredentialClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iantech.gateway.model.GatewayResponses.success;

/**
 * 平台管理员维护全平台渠道长期凭证及数据范围。
 */
@Validated
@RestController
@RequestMapping("/api/admin/platform/channel-credentials")
public class ChannelCredentialController {

    private final GatewayChannelCredentialClient channelCredentialClient;

    public ChannelCredentialController(GatewayChannelCredentialClient channelCredentialClient) {
        this.channelCredentialClient = channelCredentialClient;
    }

    @PostMapping
    public Response<ChannelCredentialSecretDTO> create(
            @Valid @RequestBody ChannelCredentialWebRequests.Create request) {
        return success(channelCredentialClient.create(CreateChannelCredentialReq.builder()
                .channelName(request.channelName()).build()));
    }

    @GetMapping
    public Response<ChannelCredentialPageDTO> queryPage(
            @Min(value = 1, message = "页码必须大于0") @RequestParam(defaultValue = "1") Integer pageNum,
            @Min(value = 1, message = "页大小必须大于0") @Max(value = 100, message = "页大小不能超过100")
            @RequestParam(defaultValue = "20") Integer pageSize,
            @Size(max = 25, message = "渠道编码长度不能超过25") @RequestParam(required = false) String channelCode,
            @Size(max = 128, message = "渠道名称长度不能超过128") @RequestParam(required = false) String channelName,
            @RequestParam(required = false) Boolean status) {
        return success(channelCredentialClient.queryPage(QueryChannelCredentialPageReq.builder()
                .pageNum(pageNum).pageSize(pageSize).channelCode(channelCode).channelName(channelName)
                .status(status).build()));
    }

    @GetMapping("/{id}")
    public Response<ChannelCredentialDTO> queryById(
            @Positive(message = "渠道凭证ID必须大于0") @PathVariable Long id) {
        return success(channelCredentialClient.queryById(QueryChannelCredentialByIdReq.builder().id(id).build()));
    }

    @PutMapping("/{id}")
    public Response<ChannelCredentialDTO> update(
            @Positive(message = "渠道凭证ID必须大于0") @PathVariable Long id,
            @Valid @RequestBody ChannelCredentialWebRequests.Update request) {
        return success(channelCredentialClient.update(UpdateChannelCredentialReq.builder()
                .id(id).channelName(request.channelName()).build()));
    }

    @PutMapping("/{id}/status")
    public Response<ChannelCredentialDTO> updateStatus(
            @Positive(message = "渠道凭证ID必须大于0") @PathVariable Long id,
            @Valid @RequestBody ChannelCredentialWebRequests.UpdateStatus request) {
        return success(channelCredentialClient.updateStatus(UpdateChannelCredentialStatusReq.builder()
                .id(id).status(request.status()).build()));
    }

    @PostMapping("/{id}/secret/rotate")
    public Response<ChannelCredentialSecretDTO> rotateSecret(
            @Positive(message = "渠道凭证ID必须大于0") @PathVariable Long id) {
        return success(channelCredentialClient.rotateSecret(RotateChannelCredentialSecretReq.builder().id(id).build()));
    }

    @DeleteMapping("/{id}")
    public Response<Boolean> delete(
            @Positive(message = "渠道凭证ID必须大于0") @PathVariable Long id) {
        return success(channelCredentialClient.delete(DeleteChannelCredentialReq.builder().id(id).build()));
    }

    @GetMapping("/{id}/data-scopes/{scopeType}")
    public Response<List<ChannelDataScopeDTO>> queryDataScopes(
            @Positive(message = "渠道凭证ID必须大于0") @PathVariable Long id,
            @PathVariable String scopeType) {
        return success(channelCredentialClient.queryDataScopes(QueryChannelDataScopesReq.builder()
                .channelId(id).scopeType(scopeType).build()));
    }

    @PutMapping("/{id}/data-scopes/{scopeType}")
    public Response<List<ChannelDataScopeDTO>> replaceDataScopes(
            @Positive(message = "渠道凭证ID必须大于0") @PathVariable Long id,
            @PathVariable String scopeType,
            @Valid @RequestBody ChannelCredentialWebRequests.ReplaceScopes request) {
        return success(channelCredentialClient.replaceDataScopes(ReplaceChannelDataScopesReq.builder()
                .channelId(id).scopeType(scopeType).scopeValues(request.scopeValues()).build()));
    }
}
