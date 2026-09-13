package cn.iantech.api.model.channel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 网关生成的渠道签名验证请求，不携带渠道密钥和完整请求体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelSignatureVerifyReq implements Serializable {
    private static final long serialVersionUID = 1L;
    private String channelCode;
    private Long secretVersion;
    private Long timestamp;
    private String signature;
    private String canonicalRequest;
}
