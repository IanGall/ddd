package cn.iantech.api.model.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * 设备会话摘要，不包含任何令牌明文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthSessionDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String sessionId;
    private String clientType;
    private String deviceId;
    private String ipAddress;
    private String userAgent;
    private Instant createdAt;
    private Instant expiresAt;
    private boolean current;
}
