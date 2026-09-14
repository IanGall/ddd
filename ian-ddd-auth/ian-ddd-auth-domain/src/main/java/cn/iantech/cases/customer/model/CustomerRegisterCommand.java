package cn.iantech.cases.customer.model;

/**
 * C 端用户注册命令。
 *
 * <p>{@code ipAddress} 为网关填充的可信客户端地址，仅用于注册入口的按 IP 风控。</p>
 */
public record CustomerRegisterCommand(String loginName, String password, String displayName, String ipAddress) {
}
