package cn.iantech.api.model.channel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplaceChannelDataScopesReq implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long channelId;
    private String scopeType;
    private List<String> scopeValues;
}
