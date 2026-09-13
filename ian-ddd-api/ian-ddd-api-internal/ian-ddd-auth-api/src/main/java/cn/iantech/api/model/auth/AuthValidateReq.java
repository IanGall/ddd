package cn.iantech.api.model.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * opaque Access Token 校验请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthValidateReq implements Serializable {
    private static final long serialVersionUID = 1L;
    private String accessToken;
}
