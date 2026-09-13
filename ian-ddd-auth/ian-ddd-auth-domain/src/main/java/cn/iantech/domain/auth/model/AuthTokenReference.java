package cn.iantech.domain.auth.model;

/**
 * 已解析的 Token 路由范围及完整 Token 摘要。
 */
public record AuthTokenReference(Long userId, String tokenHash) {

    public AuthTokenReference {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户ID必须为正数");
        }
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("Token 摘要不能为空");
        }
    }
}
