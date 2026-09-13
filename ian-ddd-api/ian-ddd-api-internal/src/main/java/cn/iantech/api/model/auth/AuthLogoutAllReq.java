package cn.iantech.api.model.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 注销当前用户全部设备会话请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthLogoutAllReq implements Serializable {
    private static final long serialVersionUID = 1L;
    private String accessToken;
}
