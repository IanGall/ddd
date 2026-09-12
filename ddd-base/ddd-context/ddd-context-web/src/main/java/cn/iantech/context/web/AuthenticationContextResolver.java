package cn.iantech.context.web;

/**
 * 将认证框架对象解析为协议无关的可信上下文字段。
 */
@FunctionalInterface
public interface AuthenticationContextResolver {

    ResolvedAuthenticationContext resolve();
}
