package cn.iantech.context.dubbo;

import cn.iantech.context.core.ContextAccessor;
import cn.iantech.context.core.RequestContext;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcContextAttachment;
import org.apache.dubbo.rpc.RpcException;

import java.util.Map;

/**
 * Dubbo 消费端上下文过滤器，将当前请求上下文写入 Client Attachment。
 */
@Activate(group = CommonConstants.CONSUMER, order = -10_000)
public class DubboContextConsumerFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        RpcContextAttachment attachment = RpcContext.getClientAttachment();
        Map<String, String> previousAttachments = DubboContextAttachments.snapshot(attachment);
        try {
            RequestContext context = ContextAccessor.current().orElseGet(RequestContext::empty);
            DubboContextAttachments.write(context, attachment);
            return invoker.invoke(invocation);
        } finally {
            // 恢复调用前状态，保证同线程嵌套调用与线程复用不会相互污染。
            DubboContextAttachments.restore(attachment, previousAttachments);
        }
    }
}
