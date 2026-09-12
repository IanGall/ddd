package cn.iantech.trigger.rpc;

import cn.iantech.api.ICustomerService;
import cn.iantech.api.model.customer.CustomerUserDTO;
import cn.iantech.cases.customer.model.CustomerRegisterCommand;
import cn.iantech.cases.customer.service.CustomerCaseService;
import cn.iantech.trigger.convertor.CustomerApiConverter;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService(version = "1.0.0", protocol = "tri", timeout = 3000)
@RequiredArgsConstructor
public class CustomerService implements ICustomerService {
    private final CustomerCaseService customerCaseService;

    @Override
    public CustomerUserDTO register(String mobile, String password, String displayName) {
        return CustomerApiConverter.toDTO(customerCaseService.register(
                new CustomerRegisterCommand(mobile, password, displayName)));
    }

    @Override
    public CustomerUserDTO authenticate(String mobile, String password) {
        return CustomerApiConverter.toDTO(customerCaseService.authenticate(mobile, password));
    }
}
