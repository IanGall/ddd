package cn.iantech.infrastructure.channel;

import cn.iantech.domain.channel.infra.ChannelEncryptedSecret;
import cn.iantech.domain.channel.infra.IChannelSecretCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 渠道密钥加解密器，主密钥只从外部配置读取。
 */
@Component
public class AesGcmChannelSecretCipher implements IChannelSecretCipher {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final String keyId;
    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmChannelSecretCipher(
            @Value("${channel.security.encryption.key-id}") String keyId,
            @Value("${channel.security.encryption.master-key}") String encodedKey) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("渠道主密钥必须为 Base64", exception);
        }
        if (keyId == null || keyId.isBlank() || keyBytes.length != 32) {
            throw new IllegalStateException("渠道主密钥配置无效，必须提供 key-id 和 256 位密钥");
        }
        this.keyId = keyId;
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public ChannelEncryptedSecret encrypt(String plaintext, String aad) {
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        return new ChannelEncryptedSecret(crypt(Cipher.ENCRYPT_MODE,
                plaintext.getBytes(StandardCharsets.UTF_8), iv, aad), iv, keyId);
    }

    @Override
    public String decrypt(ChannelEncryptedSecret encryptedSecret, String aad) {
        if (!keyId.equals(encryptedSecret.keyId())) throw new IllegalStateException("渠道密钥版本不可用");
        return new String(crypt(Cipher.DECRYPT_MODE, encryptedSecret.ciphertext(), encryptedSecret.iv(), aad),
                StandardCharsets.UTF_8);
    }

    private byte[] crypt(int mode, byte[] input, byte[] iv, String aad) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(input);
        } catch (Exception exception) {
            throw new IllegalStateException("渠道密钥加解密失败", exception);
        }
    }
}
