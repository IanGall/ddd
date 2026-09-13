package cn.iantech.cases.customer.model;

/**
 * C 端用户注册命令。
 */
public record CustomerRegisterCommand(String loginName, String password, String displayName) {
}
