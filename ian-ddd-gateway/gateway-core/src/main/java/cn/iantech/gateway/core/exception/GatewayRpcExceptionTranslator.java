package cn.iantech.gateway.core.exception;

import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static cn.iantech.common.constant.Constants.ResponseCode.*;

/**
 * 在 RPC 适配边界统一把传输失败转换为网关错误码。
 */
public final class GatewayRpcExceptionTranslator {

    private static final Logger log = LoggerFactory.getLogger(GatewayRpcExceptionTranslator.class);

    private GatewayRpcExceptionTranslator() {
    }

    public static RuntimeException translate(RuntimeException exception,
                                             Constants.ResponseCode fallbackCode) {
        AppException appException = findAppException(exception);
        if (appException != null) {
            return appException;
        }
        RpcException rpcException = findRpcException(exception);
        Constants.ResponseCode responseCode = rpcException == null
                ? fallbackCode : responseCode(rpcException);
        // 传输层失败的原因不能丢失，否则线上只能看到兜底错误码
        log.warn("下游 RPC 调用失败，转换为 {}：{}", responseCode.getCode(), exception.getMessage(), exception);
        return new AppException(responseCode.getCode(), responseCode.getInfo(), exception);
    }

    private static Constants.ResponseCode responseCode(RpcException exception) {
        if (exception.isTimeout()) {
            return RPC_TIMEOUT;
        }
        if (exception.isNoInvokerAvailableAfterFilter()) {
            return RPC_NO_PROVIDER;
        }
        return RPC_ERROR;
    }

    private static AppException findAppException(Throwable exception) {
        if (exception instanceof AppException appException) {
            return appException;
        }
        return exception.getCause() == null ? null : findAppException(exception.getCause());
    }

    private static RpcException findRpcException(Throwable exception) {
        if (exception instanceof RpcException rpcException) {
            return rpcException;
        }
        return exception.getCause() == null ? null : findRpcException(exception.getCause());
    }
}
