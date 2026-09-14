package cn.iantech.api;

import cn.iantech.api.model.customer.CustomerRegisterReq;
import cn.iantech.api.model.customer.CustomerUserDTO;
import cn.iantech.common.exception.AppException;

public interface ICustomerService {

    /**
     * C 端注册。
     *
     * <p>入参使用请求 DTO 而非裸参数：注册入口按 IP 风控，客户端 IP 是必须与业务字段一同跨 RPC 边界
     * 传递的可信字段（由网关填充，见 {@link CustomerRegisterReq#getIpAddress()}）。</p>
     */
    CustomerUserDTO register(CustomerRegisterReq request) throws AppException;

    CustomerUserDTO authenticate(String loginName, String password) throws AppException;
}
