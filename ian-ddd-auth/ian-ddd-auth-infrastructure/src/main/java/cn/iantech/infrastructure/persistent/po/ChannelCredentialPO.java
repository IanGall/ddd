package cn.iantech.infrastructure.persistent.po;

import cn.iantech.common.model.BasePO;
import cn.iantech.domain.channel.model.ChannelCredentialEntity;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ChannelCredentialEntity.class, reverseConvertGenerate = true)
public class ChannelCredentialPO extends BasePO {
    private String channelCode;
    private String channelName;
    private byte[] secretCiphertext;
    private byte[] secretIv;
    private String encryptionKeyId;
    private Long secretVersion;
    private Boolean status;
    private LocalDateTime lastRotatedAt;
    private Long createdByUserId;
    private Long updatedByUserId;
}
