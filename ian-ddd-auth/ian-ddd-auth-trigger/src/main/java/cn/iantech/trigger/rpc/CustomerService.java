package cn.iantech.trigger.rpc;

import cn.iantech.api.ICustomerService;
import cn.iantech.api.model.customer.CustomerRegisterReq;
import cn.iantech.api.model.customer.CustomerUserDTO;
import cn.iantech.cases.customer.service.CustomerCaseService;
import cn.iantech.trigger.convertor.CustomerApiConverter;
import cn.iantech.trigger.convertor.CustomerCommandConvertor;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService(version = "1.0.0", protocol = "tri", timeout = 3000)
@RequiredArgsConstructor
public class CustomerService implements ICustomerService {
    private final CustomerCaseService customerCaseService;
    private final CustomerCommandConvertor commandConvertor;

    @Override
    public CustomerUserDTO register(CustomerRegisterReq request) {
        return CustomerApiConverter.toDTO(customerCaseService.register(commandConvertor.toCommand(request)));
    }

    @Override
    public CustomerUserDTO authenticate(String loginName, String password) {
        return CustomerApiConverter.toDTO(customerCaseService.authenticate(loginName, password));
    }
}
