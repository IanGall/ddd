package cn.iantech.cases.channel.model;

import java.util.List;

/**
 * 渠道认证与凭证管理用例模型。
 */
public final class ChannelCaseModels {
    private ChannelCaseModels() {
    }

    public record SignatureCommand(String channelCode, Long secretVersion, Long timestamp, String signature,
                                   String canonicalRequest) {
    }

    public record CreateCredential(String channelName) {
    }

    public record QueryCredentialPage(Integer pageNum, Integer pageSize, String channelCode, String channelName,
                                      Boolean status) {
    }

    public record UpdateCredential(Long id, String channelName) {
    }

    public record UpdateCredentialStatus(Long id, Boolean status) {
    }

    public record QueryDataScopes(Long channelId, String scopeType) {
    }

    public record ReplaceDataScopes(Long channelId, String scopeType, List<String> scopeValues) {
    }
}
