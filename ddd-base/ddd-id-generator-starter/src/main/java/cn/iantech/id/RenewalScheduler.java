package cn.iantech.id;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * 租约续租调度器的唯一来源：一个守护线程，供同一进程内所有业务生成器共享。
 */
final class RenewalScheduler {

    private static final String THREAD_NAME = "ddd-id-generator-renewal";

    private RenewalScheduler() {
    }

    static ScheduledExecutorService singleDaemonThread() {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newSingleThreadScheduledExecutor(threadFactory);
    }
}
