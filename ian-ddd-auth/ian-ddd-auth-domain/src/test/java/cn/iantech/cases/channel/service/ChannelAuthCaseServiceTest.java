package cn.iantech.cases.channel.service;

import cn.iantech.cases.channel.model.ChannelCaseModels.SignatureCommand;
import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.domain.channel.infra.ChannelEncryptedSecret;
import cn.iantech.domain.channel.infra.IChannelCredentialRepository;
import cn.iantech.domain.channel.infra.IChannelReplayStore;
import cn.iantech.domain.channel.infra.IChannelSecretCipher;
import cn.iantech.domain.channel.model.ChannelCredentialEntity;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ChannelAuthCaseServiceTest {
    private static final String CODE = "ch_abcdefghijklmnopqrstuv";
    private static final String SECRET = "fixed-channel-secret";

    @Test
    void shouldAuthenticateAndReturnMinimalChannelIdentity() {
        AtomicBoolean consumed = new AtomicBoolean();
        ChannelAuthCaseService service = service((key, ttl) -> consumed.compareAndSet(false, true));

        var identity = service.authenticate(signedCommand(Instant.now().getEpochSecond(), "0".repeat(64)));

        assertEquals("PLATFORM_CLIENT", identity.subjectType());
        assertEquals(CODE, identity.clientId());
        assertNull(identity.ownerAccountId());
        assertEquals(1L, identity.credentialVersion());
        assertEquals(List.of("external:access"), identity.scopes());
        assertNull(identity.sessionId());
    }

    @Test
    void shouldRejectTamperedCanonicalRequest() {
        ChannelAuthCaseService service = service((key, ttl) -> true);
        SignatureCommand signed = signedCommand(Instant.now().getEpochSecond(), "0".repeat(64));
        SignatureCommand tampered = new SignatureCommand(signed.channelCode(), signed.secretVersion(), signed.timestamp(),
                signed.signature(), signed.canonicalRequest().replace("/api/external/orders", "/api/external/other"));

        assertThrows(AppException.class, () -> service.authenticate(tampered));
    }

    @Test
    void shouldRejectReplayAndExpiredTimestamp() {
        AtomicBoolean consumed = new AtomicBoolean();
        ChannelAuthCaseService service = service((key, ttl) -> consumed.compareAndSet(false, true));
        SignatureCommand command = signedCommand(Instant.now().getEpochSecond(), "0".repeat(64));

        service.authenticate(command);
        assertThrows(AppException.class, () -> service.authenticate(command));
        assertThrows(AppException.class, () -> service((key, ttl) -> true)
                .authenticate(signedCommand(Instant.now().minusSeconds(Constants.ChannelAuth.CLOCK_SKEW_SECONDS + 1L)
                        .getEpochSecond(), "0".repeat(64))));
    }

    private ChannelAuthCaseService service(IChannelReplayStore replayStore) {
        IChannelSecretCipher cipher = new IChannelSecretCipher() {
            @Override
            public ChannelEncryptedSecret encrypt(String plaintext, String aad) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String decrypt(ChannelEncryptedSecret encryptedSecret, String aad) {
                return SECRET;
            }
        };
        return new ChannelAuthCaseService(new FakeCredentialRepository(), cipher, replayStore);
    }

    private SignatureCommand signedCommand(long timestamp, String bodyHash) {
        String canonical = String.join("\n", "POST", "/api/external/orders", "", "application/json",
                CODE, "1", String.valueOf(timestamp), bodyHash);
        return new SignatureCommand(CODE, 1L, timestamp, sign(canonical), canonical);
    }

    private String sign(String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class FakeCredentialRepository implements IChannelCredentialRepository {
        @Override
        public Optional<ChannelCredentialEntity> findByChannelCode(String channelCode) {
            return Optional.of(ChannelCredentialEntity.builder().id(1L).channelCode(CODE)
                    .secretCiphertext(new byte[]{1}).secretIv(new byte[12]).encryptionKeyId("test")
                    .secretVersion(1L).status(true).deleted(false).build());
        }

        @Override
        public ChannelCredentialEntity save(ChannelCredentialEntity entity) {
            throw unsupported();
        }

        @Override
        public Optional<ChannelCredentialEntity> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public boolean existsByChannelCode(String channelCode) {
            return false;
        }

        @Override
        public long countPage(String code, String name, Boolean status) {
            return 0;
        }

        @Override
        public List<ChannelCredentialEntity> queryPage(String code, String name, Boolean status, int offset,
                                                       int size) {
            return List.of();
        }

        @Override
        public int updateName(Long id, String name, Long userId) {
            return 0;
        }

        @Override
        public int updateStatus(Long id, Boolean status, Long userId) {
            return 0;
        }

        @Override
        public int rotateSecret(Long id, Long expected, byte[] ciphertext, byte[] iv, String keyId,
                                Long next, Long userId) {
            return 0;
        }

        @Override
        public int logicDelete(Long id, Long userId) {
            return 0;
        }

        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException();
        }
    }
}
