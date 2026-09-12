package cn.iantech.context.dubbo;

import cn.iantech.context.core.ContextAccessor;
import cn.iantech.context.core.ContextScope;
import cn.iantech.context.core.RequestContext;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;

/**
 * Dubbo 提供端上下文过滤器，从 Server Attachment 建立当前调用作用域。
 */
@Activate(group = CommonConstants.PROVIDER, order = -10_000)
public class DubboContextProviderFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        RequestContext context = DubboContextAttachments.read(RpcContext.getServerAttachment());
        try (ContextScope ignored = ContextAccessor.open(context)) {
            return invoker.invoke(invocation);
        }
    }
}
