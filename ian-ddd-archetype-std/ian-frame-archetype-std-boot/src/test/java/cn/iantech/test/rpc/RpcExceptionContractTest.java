package cn.iantech.test.rpc;

import cn.iantech.api.IAuthService;
import cn.iantech.api.IRbacService;
import cn.iantech.api.model.auth.AuthValidateReq;
import cn.iantech.common.exception.AppException;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.filter.ExceptionFilter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

import static cn.iantech.common.constant.Constants.ResponseCode.AUTH_REQUIRED;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RpcExceptionContractTest {

    @Test
    void shouldDeclareAppExceptionOnEveryBusinessRpcMethod() {
        Stream.of(IAuthService.class, IRbacService.class)
                .flatMap(serviceType -> Arrays.stream(serviceType.getMethods()))
                .forEach(method -> assertTrue(Arrays.asList(method.getExceptionTypes()).contains(AppException.class),
                        () -> method.toGenericString() + " 必须声明 AppException"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldKeepDeclaredAppExceptionAfterDubboExceptionFilter() throws Exception {
        Method method = IAuthService.class.getMethod("validate", AuthValidateReq.class);
        RpcInvocation invocation = new RpcInvocation(method, IAuthService.class.getName(), "1.0.0",
                new Object[]{new AuthValidateReq()});
        Invoker<IAuthService> invoker = (Invoker<IAuthService>) mock(Invoker.class);
        when(invoker.getInterface()).thenReturn(IAuthService.class);
        AppException exception = new AppException(AUTH_REQUIRED.getCode(), "令牌无效或已过期");
        AppResponse response = new AppResponse(exception);

        new ExceptionFilter().onResponse(response, invoker, invocation);

        assertSame(exception, response.getException());
    }
}
