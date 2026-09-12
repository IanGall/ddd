package cn.iantech.api.model.channel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 创建或轮换时仅返回一次的渠道密钥。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelCredentialSecretDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String channelCode;
    private String channelSecret;
    private Long secretVersion;
}
