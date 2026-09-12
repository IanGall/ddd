package cn.iantech.test.nplusone;

import java.util.concurrent.atomic.AtomicReference;

/**
 * N+1 观测上下文：持有当前测试的观测会话。
 *
 * <p>SQL 监听器与 Dubbo filter 都通过本类上报，因此组件对未开启观测的测试完全无副作用。
 * 同一 JVM 内不允许并行执行多个 N+1 检测测试——计数是全局单例的，并发会让归属无法区分。</p>
 */
public final class NPlusOneContext {

    private static final AtomicReference<ObservationSession> ACTIVE = new AtomicReference<>();

    private NPlusOneContext() {
    }

    static void start(ObservationSession session) {
        if (!ACTIVE.compareAndSet(null, session)) {
            throw new IllegalStateException("同一 JVM 不允许并行执行多个 N+1 检测测试");
        }
    }

    static void clear(ObservationSession session) {
        ACTIVE.compareAndSet(session, null);
    }

    /**
     * 上报一次 SELECT；未开启观测时忽略。
     */
    public static void recordSelect(String sql) {
        ObservationSession session = ACTIVE.get();
        if (session != null) {
            session.recordSelect(sql);
        }
    }

    /**
     * 上报一次远程调用（{@code 接口全限定名#方法名}）；未开启观测时忽略。
     */
    public static void recordRemote(String operation) {
        ObservationSession session = ACTIVE.get();
        if (session != null) {
            session.recordRemote(operation);
        }
    }
}
