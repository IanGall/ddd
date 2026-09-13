package cn.iantech.api.model.channel;

import cn.iantech.common.model.PageRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class QueryChannelCredentialPageReq extends PageRequest {
    private static final long serialVersionUID = 1L;
    private String channelCode;
    private String channelName;
    private Boolean status;
}
