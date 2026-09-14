package cn.iantech.trigger.convertor;

import cn.iantech.api.model.customer.CustomerRegisterReq;
import cn.iantech.cases.customer.model.CustomerRegisterCommand;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * C 端请求 DTO 到用例命令的映射，MapStruct 生成实现保证字段同步与 null 源透传。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CustomerCommandConvertor {

    CustomerRegisterCommand toCommand(CustomerRegisterReq req);
}
