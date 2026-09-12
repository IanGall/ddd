package cn.iantech.domain.channel.model;

import lombok.Builder;

/**
 * 平台渠道被授予的数据范围，资源值只在服务端查询时使用。
 */
@Builder
public record ChannelDataScope(Long channelId, String scopeType, String scopeValue, Boolean status,
                               Long version, Long createdByUserId, Long updatedByUserId) {
}
