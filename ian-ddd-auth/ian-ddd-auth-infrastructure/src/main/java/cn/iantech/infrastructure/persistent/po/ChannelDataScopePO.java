package cn.iantech.infrastructure.persistent.po;

import cn.iantech.common.model.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ChannelDataScopePO extends BasePO {
    private Long channelId;
    private String scopeType;
    private String scopeValue;
    private Boolean status;
    private Long version;
    private Long createdByUserId;
    private Long updatedByUserId;
}
