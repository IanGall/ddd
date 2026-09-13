package cn.iantech.trigger.convertor;

import cn.iantech.api.model.channel.ChannelCredentialDTO;
import cn.iantech.api.model.channel.ChannelCredentialSecretDTO;
import cn.iantech.api.model.channel.ChannelDataScopeDTO;
import cn.iantech.domain.channel.model.ChannelCredentialEntity;
import cn.iantech.domain.channel.model.ChannelDataScope;
import cn.iantech.domain.channel.model.IssuedChannelCredential;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 渠道领域对象到 RPC DTO 的映射契约：公开 DTO 永不含密文/密钥材料。
 */
class ChannelApiConverterTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 13, 10, 0);

    @Test
    void shouldMapCredentialPublicFieldsOnly() {
        ChannelCredentialEntity entity = ChannelCredentialEntity.builder()
                .id(11L)
                .channelCode("CH-DEMO")
                .channelName("示例渠道")
                .secretCiphertext(new byte[]{1, 2, 3})
                .secretIv(new byte[]{4, 5, 6})
                .encryptionKeyId("kms-key-1")
                .secretVersion(5L)
                .status(Boolean.TRUE)
                .lastRotatedAt(NOW)
                .createTime(NOW)
                .updateTime(NOW)
                .build();

        ChannelCredentialDTO dto = ChannelApiConverter.toDTO(entity);

        assertEquals(11L, dto.getId());
        assertEquals("CH-DEMO", dto.getChannelCode());
        assertEquals("示例渠道", dto.getChannelName());
        assertEquals(5L, dto.getSecretVersion());
        assertEquals(Boolean.TRUE, dto.getStatus());
        assertEquals(NOW, dto.getLastRotatedAt());
        assertEquals(NOW, dto.getCreateTime());
        assertEquals(NOW, dto.getUpdateTime());
    }

    @Test
    void shouldNotExposeCipherOrKeyMaterial() {
        boolean leaked = Arrays.stream(ChannelCredentialDTO.class.getDeclaredFields())
                .anyMatch(field -> field.getName().toLowerCase().contains("cipher")
                        || field.getName().equalsIgnoreCase("secretIv")
                        || field.getName().toLowerCase().contains("encryptionkey"));

        assertTrue(!leaked, "ChannelCredentialDTO 不得包含密文/密钥字段");
    }

    @Test
    void shouldMapIssuedSecretForOneTimeDelivery() {
        ChannelCredentialEntity entity = ChannelCredentialEntity.builder()
                .id(21L).channelCode("CH-X").secretVersion(2L).build();

        ChannelCredentialSecretDTO dto = ChannelApiConverter.toSecretDTO(
                new IssuedChannelCredential(entity, "plain-secret-once"));

        assertEquals(21L, dto.getId());
        assertEquals("CH-X", dto.getChannelCode());
        assertEquals(2L, dto.getSecretVersion());
        assertEquals("plain-secret-once", dto.getChannelSecret());
    }

    @Test
    void shouldMapDataScope() {
        ChannelDataScope scope = ChannelDataScope.builder()
                .channelId(31L).scopeType("SHOP").scopeValue("shop-1").build();

        ChannelDataScopeDTO dto = ChannelApiConverter.toScopeDTO(scope);

        assertEquals("SHOP", dto.getScopeType());
        assertEquals("shop-1", dto.getScopeValue());
    }
}
