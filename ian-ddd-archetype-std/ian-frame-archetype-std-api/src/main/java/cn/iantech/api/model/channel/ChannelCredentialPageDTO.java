package cn.iantech.api.model.channel;

import cn.iantech.common.model.PageResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ChannelCredentialPageDTO extends PageResponse<ChannelCredentialDTO> {
    private static final long serialVersionUID = 1L;
}
