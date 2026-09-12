package cn.iantech.domain.channel.infra;

public interface IChannelSecretCipher {

    ChannelEncryptedSecret encrypt(String plaintext, String aad);

    String decrypt(ChannelEncryptedSecret encryptedSecret, String aad);
}
