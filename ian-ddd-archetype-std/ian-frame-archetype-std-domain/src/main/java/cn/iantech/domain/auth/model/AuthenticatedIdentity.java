package cn.iantech.domain.auth.model;

import java.util.List;

/**
 * 不同身份域认证成功后交给 Auth 的统一身份快照。
 */
public record AuthenticatedIdentity(Long userId, Long accountId, String username, String userType,
                                    List<String> roleCodes, List<String> permissionCodes) {
}
