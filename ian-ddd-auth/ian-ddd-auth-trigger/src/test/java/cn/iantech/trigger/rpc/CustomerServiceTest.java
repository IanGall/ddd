package cn.iantech.trigger.rpc;

import cn.iantech.api.model.customer.CustomerUserDTO;
import cn.iantech.cases.customer.model.CustomerRegisterCommand;
import cn.iantech.cases.customer.service.CustomerCaseService;
import cn.iantech.domain.customer.model.CustomerUserEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C 端用户 Dubbo 入站适配器的委托与 DTO 转换。
 */
class CustomerServiceTest {

    private static final CustomerUserEntity ENTITY = CustomerUserEntity.builder()
            .id(9001L).loginName("13800000000").displayName("C 端用户").avatar("").status(Boolean.TRUE).build();

    private final CustomerCaseService customerCaseService = mock(CustomerCaseService.class);
    private final CustomerService service = new CustomerService(customerCaseService);

    @Test
    void shouldMapRegisterArgumentsAndConvertToDto() {
        when(customerCaseService.register(any())).thenReturn(ENTITY);

        CustomerUserDTO dto = service.register("13800000000", "pwd-1234", "C 端用户");

        assertEquals(9001L, dto.getId());
        assertEquals("13800000000", dto.getLoginName());
        assertEquals("C 端用户", dto.getDisplayName());
        assertEquals(Boolean.TRUE, dto.getStatus());

        ArgumentCaptor<CustomerRegisterCommand> captor = ArgumentCaptor.forClass(CustomerRegisterCommand.class);
        verify(customerCaseService).register(captor.capture());
        assertEquals("13800000000", captor.getValue().loginName());
        assertEquals("pwd-1234", captor.getValue().password());
        assertEquals("C 端用户", captor.getValue().displayName());
    }

    @Test
    void shouldDelegateAuthenticateWithOriginalArguments() {
        when(customerCaseService.authenticate("13800000000", "pwd-1234")).thenReturn(ENTITY);

        CustomerUserDTO dto = service.authenticate("13800000000", "pwd-1234");

        assertEquals(9001L, dto.getId());
        verify(customerCaseService).authenticate("13800000000", "pwd-1234");
    }
}
