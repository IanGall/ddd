package cn.iantech.gateway.core.service;

import cn.iantech.common.constant.Constants;
import cn.iantech.gateway.core.exception.GatewayRpcExceptionTranslator;

import java.util.function.Supplier;

/**
 * 统一隔离 Dubbo 调用异常，转换为网关协议异常。
 */
public final class RpcCallGuard {

    private RpcCallGuard() {
    }

    public static <T> T call(Supplier<T> invocation, Constants.ResponseCode fallback) {
        try {
            return invocation.get();
        } catch (RuntimeException exception) {
            throw GatewayRpcExceptionTranslator.translate(exception, fallback);
        }
    }

    public static void call(Runnable invocation, Constants.ResponseCode fallback) {
        try {
            invocation.run();
        } catch (RuntimeException exception) {
            throw GatewayRpcExceptionTranslator.translate(exception, fallback);
        }
    }
}
