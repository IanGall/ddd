package cn.iantech.trigger.convertor;

import cn.iantech.api.model.channel.*;
import cn.iantech.cases.channel.model.ChannelCaseModels.*;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 渠道凭证请求 DTO 到用例命令的映射，MapStruct 生成实现保证字段同步与 null 源透传。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ChannelCredentialCommandConvertor {

    CreateCredential toCommand(CreateChannelCredentialReq req);

    QueryCredentialPage toCommand(QueryChannelCredentialPageReq req);

    UpdateCredential toCommand(UpdateChannelCredentialReq req);

    UpdateCredentialStatus toCommand(UpdateChannelCredentialStatusReq req);

    QueryDataScopes toCommand(QueryChannelDataScopesReq req);

    ReplaceDataScopes toCommand(ReplaceChannelDataScopesReq req);
}
