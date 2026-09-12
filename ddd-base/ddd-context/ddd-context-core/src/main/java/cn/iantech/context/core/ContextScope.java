package cn.iantech.context.core;

/** 可嵌套的上下文作用域，关闭时恢复进入作用域前的上下文。 */
public final class ContextScope implements AutoCloseable {

    private final RequestContext previous;
    private boolean closed;

    ContextScope(RequestContext previous) {
        this.previous = previous;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            ContextAccessor.restore(previous);
        }
    }
}
