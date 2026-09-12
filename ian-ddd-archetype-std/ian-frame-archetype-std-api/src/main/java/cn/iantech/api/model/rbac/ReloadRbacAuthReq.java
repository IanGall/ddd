package cn.iantech.api.model.rbac;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 根据服务端可信会话重新加载认证身份。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReloadRbacAuthReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long accountId;

    private Long userId;

    private String userType;
}
