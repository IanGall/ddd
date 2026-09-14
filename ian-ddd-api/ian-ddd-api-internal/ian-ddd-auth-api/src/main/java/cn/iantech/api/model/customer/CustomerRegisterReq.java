package cn.iantech.api.model.customer;

import lombok.Data;

import java.io.Serializable;

/**
 * C 端注册请求。
 *
 * <p>{@code ipAddress} 由网关按 {@code HttpServletRequest#getRemoteAddr()} 填充，认证服务据此对注册
 * 入口做按 IP 风控（与登录路径同一套风控存储，但使用独立计数命名空间）。客户端不得自行提交该字段，
 * 网关也不得直接透传客户端提供的同名字段。</p>
 */
@Data
public class CustomerRegisterReq implements Serializable {
    private static final long serialVersionUID = 1L;
    private String loginName;
    private String password;
    private String displayName;
    private String ipAddress;
}
