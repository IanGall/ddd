package cn.iantech.trigger.rpc;

import cn.iantech.api.model.customer.CustomerRegisterReq;
import cn.iantech.api.model.customer.CustomerUserDTO;
import cn.iantech.cases.customer.model.CustomerRegisterCommand;
import cn.iantech.cases.customer.service.CustomerCaseService;
import cn.iantech.domain.customer.model.CustomerUserEntity;
import cn.iantech.trigger.convertor.CustomerCommandConvertor;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C 端用户 Dubbo 入站适配器的委托与 DTO 转换。
 *
 * <p>注册入参含网关填充的可信客户端 IP，适配器必须原样搬进用例命令——
 * 按 IP 风控完全依赖该字段，漏传会使风控退化为单一共享计数桶。</p>
 */
class CustomerServiceTest {

    private static final CustomerUserEntity ENTITY = CustomerUserEntity.builder()
            .id(9001L).loginName("13800000000").displayName("C 端用户").avatar("").status(Boolean.TRUE).build();

    private final CustomerCaseService customerCaseService = mock(CustomerCaseService.class);
    private final CustomerCommandConvertor commandConvertor = Mappers.getMapper(CustomerCommandConvertor.class);
    private final CustomerService service = new CustomerService(customerCaseService, commandConvertor);

    @Test
    void shouldMapRegisterRequestIncludingClientIpAndConvertToDto() {
        when(customerCaseService.register(any())).thenReturn(ENTITY);
        CustomerRegisterReq request = new CustomerRegisterReq();
        request.setLoginName("13800000000");
        request.setPassword("pwd-1234");
        request.setDisplayName("C 端用户");
        request.setIpAddress("10.0.0.1");

        CustomerUserDTO dto = service.register(request);

        assertEquals(9001L, dto.getId());
        assertEquals("13800000000", dto.getLoginName());
        assertEquals("C 端用户", dto.getDisplayName());
        assertEquals(Boolean.TRUE, dto.getStatus());

        ArgumentCaptor<CustomerRegisterCommand> captor = ArgumentCaptor.forClass(CustomerRegisterCommand.class);
        verify(customerCaseService).register(captor.capture());
        assertEquals("13800000000", captor.getValue().loginName());
        assertEquals("pwd-1234", captor.getValue().password());
        assertEquals("C 端用户", captor.getValue().displayName());
        assertEquals("10.0.0.1", captor.getValue().ipAddress());
    }

    @Test
    void shouldPassNullRequestThroughAsNullCommand() {
        // 空请求由用例层的前置校验拒绝；适配器只需保证 null 源映射仍是 null、不会自行抛异常
        when(customerCaseService.register(isNull())).thenReturn(ENTITY);

        CustomerUserDTO dto = service.register(null);

        assertEquals(9001L, dto.getId());
        verify(customerCaseService).register(isNull());
    }

    @Test
    void shouldDelegateAuthenticateWithOriginalArguments() {
        when(customerCaseService.authenticate("13800000000", "pwd-1234")).thenReturn(ENTITY);

        CustomerUserDTO dto = service.authenticate("13800000000", "pwd-1234");

        assertEquals(9001L, dto.getId());
        verify(customerCaseService).authenticate("13800000000", "pwd-1234");
    }
}
