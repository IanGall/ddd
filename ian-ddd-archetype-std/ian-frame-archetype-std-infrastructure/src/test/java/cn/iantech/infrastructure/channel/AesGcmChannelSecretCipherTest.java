package cn.iantech.infrastructure.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AesGcmChannelSecretCipherTest {
    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Test
    void shouldRoundTripWithBoundAad() {
        AesGcmChannelSecretCipher cipher = new AesGcmChannelSecretCipher("test-key", KEY);
        var encrypted = cipher.encrypt("channel-secret", "9:ch_test:1");

        assertEquals("channel-secret", cipher.decrypt(encrypted, "9:ch_test:1"));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(encrypted, "9:ch_test:2"));
    }

    @Test
    void shouldRequire256BitMasterKey() {
        assertThrows(IllegalStateException.class,
                () -> new AesGcmChannelSecretCipher("test-key", "c2hvcnQ="));
    }
}
