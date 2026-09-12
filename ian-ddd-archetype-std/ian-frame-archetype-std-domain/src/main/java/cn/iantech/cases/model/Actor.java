package cn.iantech.cases.model;

import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;

/**
 * Trigger 从可信上下文解析出的领域操作者。
 */
public record Actor(String requestId, Long accountId, Long userId, String principalName,
                    String subjectType, String source) {

    public Actor {
        if (blank(requestId) || accountId == null || accountId <= 0 || userId == null || userId <= 0
                || blank(principalName) || blank(subjectType) || !"gateway".equals(source)) {
            throw new AppException(Constants.ResponseCode.ACCESS_DENIED.getCode(), "无权访问");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
