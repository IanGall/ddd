/**
 * 外部 HTTP 契约包。
 *
 * <p>与 {@code cn.iantech.api} 下的内部 Dubbo RPC 契约分开放置：这里只描述对<b>外部/第三方服务</b>的 HTTP 调用，
 * 使用 Spring 声明式 HTTP 接口（{@code @HttpExchange} + {@code @GetExchange}/{@code @PostExchange} …），
 * 由 {@code HttpServiceProxyFactory} + {@code RestClient} 生成代理，不引入 Spring Cloud 或 OpenFeign。</p>
 *
 * <p>约定：每个外部服务一个接口；请求/响应模型放在该服务对应的 {@code model} 子包；
 * 接口上标注 {@code @HttpExchange} 并在类级声明基础路径与 {@code Accept}。</p>
 */
package cn.iantech.api.external;
