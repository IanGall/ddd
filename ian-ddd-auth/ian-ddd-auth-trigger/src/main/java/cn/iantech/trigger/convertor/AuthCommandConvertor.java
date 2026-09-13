package cn.iantech.trigger.convertor;

import cn.iantech.api.model.auth.AuthLoginReq;
import cn.iantech.api.model.auth.AuthRefreshReq;
import cn.iantech.api.model.channel.ChannelSignatureVerifyReq;
import cn.iantech.api.model.customer.CustomerLoginReq;
import cn.iantech.cases.auth.model.AuthCaseModels.LoginCommand;
import cn.iantech.cases.auth.model.AuthCaseModels.RefreshCommand;
import cn.iantech.cases.channel.model.ChannelCaseModels.SignatureCommand;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 认证请求 DTO 到用例命令的映射，MapStruct 生成实现保证字段同步与 null 源透传。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AuthCommandConvertor {

    LoginCommand toCommand(AuthLoginReq req);

    LoginCommand toCommand(CustomerLoginReq req);

    RefreshCommand toCommand(AuthRefreshReq req);

    SignatureCommand toCommand(ChannelSignatureVerifyReq req);
}
