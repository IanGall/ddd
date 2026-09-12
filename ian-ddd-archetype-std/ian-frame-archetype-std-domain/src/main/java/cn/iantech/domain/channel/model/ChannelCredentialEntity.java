package cn.iantech.domain.channel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 渠道长期凭证聚合。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelCredentialEntity {
    private Long id;
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
    private Boolean deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
