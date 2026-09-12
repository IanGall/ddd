package cn.iantech.gateway.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 渠道凭证管理 HTTP 请求模型。
 */
public final class ChannelCredentialWebRequests {

    private ChannelCredentialWebRequests() {
    }

    public record Create(
            @NotBlank(message = "渠道名称不能为空")
            @Size(max = 128, message = "渠道名称长度不能超过128") String channelName) {
    }

    public record Update(
            @NotBlank(message = "渠道名称不能为空")
            @Size(max = 128, message = "渠道名称长度不能超过128") String channelName) {
    }

    public record UpdateStatus(@NotNull(message = "渠道状态不能为空") Boolean status) {
    }

    public record ReplaceScopes(
            @NotNull(message = "范围值列表不能为空")
            @Size(max = 1000, message = "单次最多配置1000个范围值") List<@NotBlank String> scopeValues) {
    }
}
