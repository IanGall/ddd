package cn.iantech.domain.channel.infra;

public record ChannelEncryptedSecret(byte[] ciphertext, byte[] iv, String keyId) {
}
