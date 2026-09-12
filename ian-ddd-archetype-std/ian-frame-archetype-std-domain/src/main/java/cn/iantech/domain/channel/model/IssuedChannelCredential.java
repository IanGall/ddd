package cn.iantech.domain.channel.model;

/**
 * 领域操作产生的一次性明文密钥，只允许交给创建或轮换调用方。
 */
public record IssuedChannelCredential(ChannelCredentialEntity credential, String channelSecret) {
}
