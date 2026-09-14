package cn.iantech.id;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 持有一个 Worker ID 租约的 ID 生成器。
 *
 * <p>出号与关闭在同一条实例锁上互斥：只有「已关闭」状态对出号线程可见之后，
 * 租约才会被释放，避免释放瞬间另一个实例租到同一 Worker ID 而产生重复 ID。
 */
final class LeaseBackedGlobalIdGenerator implements GlobalIdGenerator, AutoCloseable {

    private final RedisWorkerLease workerLease;
    private final GlobalIdGenerator delegate;
    private final Object lifecycleLock = new Object();
    private final ScheduledFuture<?> renewalTask;

    /** 仅当调度器由本实例创建时才非空，关闭时一并销毁。 */
    private final ScheduledExecutorService ownedScheduler;

    private boolean closed;

    LeaseBackedGlobalIdGenerator(RedisWorkerLease workerLease, GlobalIdGenerator delegate,
                                 ScheduledExecutorService scheduler, long renewIntervalMillis) {
        this(workerLease, delegate, scheduler, renewIntervalMillis, null);
    }

    /**
     * 创建自持续租调度器的生成器，适用于没有共享方的独立场景；关闭时一并销毁调度器。
     */
    static LeaseBackedGlobalIdGenerator withOwnScheduler(RedisWorkerLease workerLease,
                                                         GlobalIdGenerator delegate,
                                                         long renewIntervalMillis) {
        ScheduledExecutorService scheduler = RenewalScheduler.singleDaemonThread();
        return new LeaseBackedGlobalIdGenerator(workerLease, delegate, scheduler, renewIntervalMillis, scheduler);
    }

    private LeaseBackedGlobalIdGenerator(RedisWorkerLease workerLease, GlobalIdGenerator delegate,
                                         ScheduledExecutorService scheduler, long renewIntervalMillis,
                                         ScheduledExecutorService ownedScheduler) {
        this.workerLease = Objects.requireNonNull(workerLease, "Worker 租约不能为空");
        this.delegate = Objects.requireNonNull(delegate, "ID 生成器不能为空");
        Objects.requireNonNull(scheduler, "续租调度器不能为空");
        this.ownedScheduler = ownedScheduler;
        this.renewalTask = scheduler.scheduleWithFixedDelay(
                workerLease::renew, renewIntervalMillis, renewIntervalMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public long nextId() {
        synchronized (lifecycleLock) {
            if (closed || !workerLease.isValid()) {
                throw new IdGenerationException("Worker ID 租约已失效，拒绝生成 ID");
            }
            try {
                return delegate.nextId();
            } catch (RuntimeException exception) {
                throw new IdGenerationException("生成 ID 失败", exception);
            }
        }
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            closed = true;
            renewalTask.cancel(false);
        }
        // 释放租约包含 Redis 调用，放在锁外，避免拖长出号临界区。
        workerLease.close();
        if (ownedScheduler != null) {
            ownedScheduler.shutdownNow();
        }
    }
}
