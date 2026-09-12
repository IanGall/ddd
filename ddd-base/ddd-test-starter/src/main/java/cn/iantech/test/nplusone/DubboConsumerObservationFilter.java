package cn.iantech.test.nplusone;

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

/**
 * Dubbo 消费端观测过滤器：一次远程调用上报一次，用于发现「逐元素调用下游」的 N+1 远程调用。
 *
 * <p>通过 {@code META-INF/dubbo/org.apache.dubbo.rpc.Filter} 以 SPI 自动激活；未开启观测时
 * 上报为空操作，对正常调用链无影响。</p>
 */
@Activate(group = "consumer")
public final class DubboConsumerObservationFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        NPlusOneContext.recordRemote(invoker.getInterface().getName() + "#" + invocation.getMethodName());
        return invoker.invoke(invocation);
    }
}
