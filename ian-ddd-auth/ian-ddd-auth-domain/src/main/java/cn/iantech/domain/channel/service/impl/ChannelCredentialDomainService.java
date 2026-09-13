package cn.iantech.domain.channel.service.impl;

import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.domain.channel.infra.ChannelEncryptedSecret;
import cn.iantech.domain.channel.infra.IChannelCredentialRepository;
import cn.iantech.domain.channel.infra.IChannelSecretCipher;
import cn.iantech.domain.channel.model.ChannelCredentialEntity;
import cn.iantech.domain.channel.model.IssuedChannelCredential;
import cn.iantech.domain.channel.service.IChannelCredentialDomainService;
import cn.iantech.domain.model.DomainPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.stream.IntStream;

@RequiredArgsConstructor
@Service
public class ChannelCredentialDomainService implements IChannelCredentialDomainService {
    private static final int SECRET_BYTES = 32;
    private static final int CODE_BYTES = 16;
    private static final int MAX_CREATE_ATTEMPTS = 8;

    private final IChannelCredentialRepository repository;
    private final IChannelSecretCipher secretCipher;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public IssuedChannelCredential create(Long userId, String channelName) {
        requirePositive(userId, "用户无效");
        String normalizedName = requireName(channelName);
        String channelCode = uniqueChannelCode();
        String secret = randomValue(SECRET_BYTES);
        long version = 1L;
        ChannelEncryptedSecret encrypted = secretCipher.encrypt(secret, aad(channelCode, version));
        ChannelCredentialEntity entity = repository.save(ChannelCredentialEntity.builder()
                .channelCode(channelCode).channelName(normalizedName)
                .secretCiphertext(encrypted.ciphertext()).secretIv(encrypted.iv())
                .encryptionKeyId(encrypted.keyId()).secretVersion(version).status(true)
                .lastRotatedAt(LocalDateTime.now()).createdByUserId(userId).updatedByUserId(userId)
                .deleted(false).build());
        return new IssuedChannelCredential(entity, secret);
    }

    @Override
    public DomainPage<ChannelCredentialEntity> queryPage(int pageNum, int pageSize,
                                           String channelCode, String channelName, Boolean status) {
        int normalizedPage = Math.max(pageNum, 1);
        int normalizedSize = Math.clamp(pageSize, 1, 100);
        long total = repository.countPage(channelCode, channelName, status);
        var list = repository.queryPage(channelCode, channelName, status,
                (normalizedPage - 1) * normalizedSize, normalizedSize);
        return new DomainPage<>(total, normalizedPage, normalizedSize, list);
    }

    @Override
    public ChannelCredentialEntity queryById(Long id) {
        return repository.findById(id).orElseThrow(this::notFound);
    }

    @Override
    public ChannelCredentialEntity update(Long userId, Long id, String channelName) {
        queryById(id);
        if (repository.updateName(id, requireName(channelName), userId) != 1) throw conflict();
        return queryById(id);
    }

    @Override
    public ChannelCredentialEntity updateStatus(Long userId, Long id, Boolean status) {
        queryById(id);
        if (status == null) throw invalid("状态不能为空");
        if (repository.updateStatus(id, status, userId) != 1) throw conflict();
        return queryById(id);
    }

    @Override
    public IssuedChannelCredential rotateSecret(Long userId, Long id) {
        ChannelCredentialEntity current = queryById(id);
        long nextVersion = current.getSecretVersion() + 1;
        String secret = randomValue(SECRET_BYTES);
        ChannelEncryptedSecret encrypted = secretCipher.encrypt(secret,
                aad(current.getChannelCode(), nextVersion));
        int updated = repository.rotateSecret(id, current.getSecretVersion(), encrypted.ciphertext(),
                encrypted.iv(), encrypted.keyId(), nextVersion, userId);
        if (updated != 1) throw conflict();
        return new IssuedChannelCredential(queryById(id), secret);
    }

    @Override
    public boolean delete(Long userId, Long id) {
        queryById(id);
        return repository.logicDelete(id, userId) == 1;
    }

    private String uniqueChannelCode() {
        return IntStream.range(0, MAX_CREATE_ATTEMPTS)
                .mapToObj(ignored -> "ch_" + randomValue(CODE_BYTES))
                .filter(code -> !repository.existsByChannelCode(code))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("无法生成唯一渠道编码"));
    }

    private String randomValue(int bytes) {
        byte[] value = new byte[bytes];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String aad(String channelCode, long version) {
        return channelCode + ":" + version;
    }

    private String requireName(String value) {
        if (value == null || value.isBlank() || value.length() > 128) throw invalid("渠道名称长度必须为1到128位");
        return value.trim();
    }

    private void requirePositive(Long value, String message) {
        if (value == null || value <= 0) throw invalid(message);
    }

    private AppException invalid(String message) {
        return new AppException(Constants.ResponseCode.INVALID_ARGUMENT.getCode(), message);
    }

    private AppException notFound() {
        return new AppException(Constants.ResponseCode.NOT_FOUND.getCode(), "渠道凭证不存在");
    }

    private AppException conflict() {
        return new AppException(Constants.ResponseCode.CONFLICT.getCode(), "渠道凭证已发生变化，请重试");
    }
}
