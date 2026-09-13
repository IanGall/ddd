package cn.iantech.api.model.customer;

import lombok.Data;

import java.io.Serializable;

@Data
public class CustomerLoginReq implements Serializable {
    private static final long serialVersionUID = 1L;
    private String loginName;
    private String password;
    private String clientType;
    private String deviceId;
    private String ipAddress;
    private String userAgent;
}
