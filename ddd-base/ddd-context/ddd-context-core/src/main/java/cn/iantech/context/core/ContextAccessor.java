package cn.iantech.context.core;

import java.util.Optional;

/** 统一上下文访问入口，业务代码不直接依赖 ThreadLocal。 */
public final class ContextAccessor {

    private static final ThreadLocal<RequestContext> HOLDER = new ThreadLocal<>();

    private ContextAccessor() {
    }

    public static Optional<RequestContext> current() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static ContextScope open(RequestContext context) {
        RequestContext previous = HOLDER.get();
        if (context == null || context.isEmpty()) {
            HOLDER.remove();
        } else {
            HOLDER.set(context);
        }
        return new ContextScope(previous);
    }

    static void restore(RequestContext previous) {
        if (previous == null) {
            HOLDER.remove();
        } else {
            HOLDER.set(previous);
        }
    }

    static RequestContext peek() {
        return HOLDER.get();
    }
}
