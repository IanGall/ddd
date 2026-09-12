package cn.iantech.api.model.channel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 渠道凭证公开信息，永不包含密钥材料。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelCredentialDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String channelCode;
    private String channelName;
    private Long secretVersion;
    private Boolean status;
    private LocalDateTime lastRotatedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
