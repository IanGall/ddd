package cn.iantech.context.core;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/** 不可变上下文快照，用于后续异步任务或回调传播。 */
public final class ContextSnapshot {

    private final RequestContext context;

    private ContextSnapshot(RequestContext context) {
        this.context = context;
    }

    public static ContextSnapshot capture() {
        return new ContextSnapshot(ContextAccessor.peek());
    }

    public boolean isEmpty() {
        return context == null;
    }

    public RequestContext context() {
        return context == null ? RequestContext.empty() : context;
    }

    public Runnable wrap(Runnable task) {
        Objects.requireNonNull(task, "任务不能为空");
        return () -> {
            try (ContextScope ignored = ContextAccessor.open(context)) {
                task.run();
            }
        };
    }

    public <T> Callable<T> wrap(Callable<T> task) {
        Objects.requireNonNull(task, "任务不能为空");
        return () -> {
            try (ContextScope ignored = ContextAccessor.open(context)) {
                return task.call();
            }
        };
    }

    public <T> Supplier<T> wrap(Supplier<T> task) {
        Objects.requireNonNull(task, "任务不能为空");
        return () -> {
            try (ContextScope ignored = ContextAccessor.open(context)) {
                return task.get();
            }
        };
    }
}
