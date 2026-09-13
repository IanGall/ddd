package cn.iantech.trigger.context;

import cn.iantech.cases.model.Actor;
import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.context.core.ContextAccessor;
import cn.iantech.context.core.RequestContext;
import org.springframework.stereotype.Component;

/**
 * 将入口层可信上下文转换为显式用例操作者。
 */
@Component
public class ActorResolver {

    public Actor resolve() {
        RequestContext context = ContextAccessor.current().orElseThrow(this::accessDenied);
        return new Actor(context.requestId(), parsePositiveId(context.tenantId()),
                parsePositiveId(context.userId()), context.principalName(), context.subjectType(), context.source());
    }

    private Long parsePositiveId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw accessDenied();
            }
            return id;
        } catch (NumberFormatException | NullPointerException exception) {
            throw accessDenied();
        }
    }

    private AppException accessDenied() {
        return new AppException(Constants.ResponseCode.ACCESS_DENIED.getCode(), "无权访问");
    }
}
