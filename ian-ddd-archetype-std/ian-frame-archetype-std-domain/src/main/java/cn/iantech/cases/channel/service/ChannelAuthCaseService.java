package cn.iantech.cases.channel.service;

import cn.iantech.cases.auth.model.AuthCaseModels.IdentityResult;
import cn.iantech.cases.channel.model.ChannelCaseModels.SignatureCommand;
import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.common.util.Sha256;
import cn.iantech.domain.channel.infra.ChannelEncryptedSecret;
import cn.iantech.domain.channel.infra.IChannelCredentialRepository;
import cn.iantech.domain.channel.infra.IChannelReplayStore;
import cn.iantech.domain.channel.infra.IChannelSecretCipher;
import cn.iantech.domain.channel.model.ChannelCredentialEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 渠道 HMAC 认证用例，所有校验失败统一返回认证错误。
 */
@Service
@RequiredArgsConstructor
public class ChannelAuthCaseService {
    private static final String PLATFORM_CLIENT_SUBJECT_TYPE = "PLATFORM_CLIENT";
    private static final long ALLOWED_CLOCK_SKEW_SECONDS = 300;
    private static final Duration REPLAY_TTL = Duration.ofSeconds(600);
    private static final int CANONICAL_LINES = 8;
    private static final int MAX_CANONICAL_LENGTH = 16 * 1024;
    private static final Pattern CHANNEL_CODE = Pattern.compile("ch_[A-Za-z0-9_-]{22}");
    private static final Pattern SIGNATURE = Pattern.compile("[0-9a-f]{64}");

    private final IChannelCredentialRepository credentialRepository;
    private final IChannelSecretCipher secretCipher;
    private final IChannelReplayStore replayStore;

    public IdentityResult authenticate(SignatureCommand command) {
        validateRequest(command);
        ChannelCredentialEntity credential = credentialRepository.findByChannelCode(command.channelCode())
                .filter(item -> Boolean.TRUE.equals(item.getStatus()) && !Boolean.TRUE.equals(item.getDeleted()))
                .filter(item -> command.secretVersion().equals(item.getSecretVersion()))
                .orElseThrow(this::unauthorized);
        String secret;
        try {
            secret = secretCipher.decrypt(new ChannelEncryptedSecret(credential.getSecretCiphertext(),
                            credential.getSecretIv(), credential.getEncryptionKeyId()),
                    aad(credential.getChannelCode(), credential.getSecretVersion()));
        } catch (RuntimeException exception) {
            throw unauthorized();
        }
        if (!MessageDigest.isEqual(hmac(secret, command.canonicalRequest()), decodeSignature(command.signature()))) {
            throw unauthorized();
        }
        String replayKey = Sha256.hex(command.channelCode() + ":" + command.signature());
        if (!replayStore.markIfAbsent(replayKey, REPLAY_TTL)) {
            throw unauthorized();
        }
        return new IdentityResult(null, null, credential.getChannelCode(), null, PLATFORM_CLIENT_SUBJECT_TYPE,
                credential.getChannelCode(), credential.getChannelCode(),
                List.of(Constants.AuthScope.EXTERNAL_ACCESS), "channel-auth",
                Constants.TokenKind.CHANNEL_HMAC, null, null, credential.getSecretVersion(),
                Constants.AuthScope.EXTERNAL_ACCESS);
    }

    private void validateRequest(SignatureCommand command) {
        if (command == null || command.channelCode() == null || !CHANNEL_CODE.matcher(command.channelCode()).matches()
                || command.secretVersion() == null || command.secretVersion() <= 0
                || command.timestamp() == null || command.timestamp() <= 0
                || outsideAllowedTimeWindow(command.timestamp())
                || command.signature() == null || !SIGNATURE.matcher(command.signature()).matches()
                || command.canonicalRequest() == null || command.canonicalRequest().length() > MAX_CANONICAL_LENGTH
                || command.canonicalRequest().indexOf('\r') >= 0) {
            throw unauthorized();
        }
        String[] lines = command.canonicalRequest().split("\\n", -1);
        if (lines.length != CANONICAL_LINES || lines[0].isBlank() || lines[1].isBlank()
                || !lines[0].matches("[A-Z]+") || !lines[1].startsWith("/") || lines[1].contains("\\")
                || !lines[2].matches("[A-Za-z0-9._~%=&-]*")
                || !(lines[3].isEmpty() || "application/json".equals(lines[3]))
                || !command.channelCode().equals(lines[4])
                || !String.valueOf(command.secretVersion()).equals(lines[5])
                || !String.valueOf(command.timestamp()).equals(lines[6])
                || !lines[7].matches("[0-9a-f]{64}")) {
            throw unauthorized();
        }
    }

    private boolean outsideAllowedTimeWindow(long timestamp) {
        long now = Instant.now().getEpochSecond();
        return timestamp < now - ALLOWED_CLOCK_SKEW_SECONDS || timestamp > now + ALLOWED_CLOCK_SKEW_SECONDS;
    }

    private byte[] hmac(String secret, String canonicalRequest) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("JVM 不支持 HmacSHA256", exception);
        }
    }

    private byte[] decodeSignature(String signature) {
        try {
            return HexFormat.of().parseHex(signature);
        } catch (IllegalArgumentException exception) {
            throw unauthorized();
        }
    }

    private String aad(String channelCode, Long secretVersion) {
        return channelCode + ":" + secretVersion;
    }

    private AppException unauthorized() {
        return new AppException(Constants.ResponseCode.AUTH_REQUIRED.getCode(), "渠道认证失败");
    }
}
