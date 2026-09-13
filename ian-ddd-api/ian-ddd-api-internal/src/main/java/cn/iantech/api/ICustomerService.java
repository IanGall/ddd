package cn.iantech.api;

import cn.iantech.api.model.customer.CustomerUserDTO;
import cn.iantech.common.exception.AppException;

public interface ICustomerService {
    CustomerUserDTO register(String loginName, String password, String displayName) throws AppException;

    CustomerUserDTO authenticate(String loginName, String password) throws AppException;
}
