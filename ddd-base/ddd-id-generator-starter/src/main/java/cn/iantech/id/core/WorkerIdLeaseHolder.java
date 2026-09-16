package cn.iantech.id.core;

import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.id.IdGenerationException;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 应用实例级的 Worker ID 租约持有者：一个实例一个租约，实例内所有业务生成器共用它。
 *
 * <p>出号取**读锁**（各业务之间不互相阻塞，业务内部仍由 {@link SnowflakeIdGenerator} 的实例锁串行），
 * 关闭取**写锁**。写锁的获取必须等所有持读锁的出号线程退出，因此
 * 「已关闭」状态对出号线程可见之后才会释放租约，避免释放瞬间另一个实例租到同一 Worker ID
 * 而产生重复 ID。
 *
 * <p>租约是实例级的，所以一次租约失效会同时停掉本实例内的全部业务生成器；这是相
 * 比「每业务各持一个租约」在隔离性上的净损失，已在文档中说明。
 */
final class WorkerIdLeaseHolder implements AutoCloseable {

    private final RedisWorkerLease workerLease;
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private final ScheduledFuture<?> renewalTask;

    /** 仅当调度器由本实例创建时才非空，关闭时一并销毁。 */
    private final ScheduledExecutorService ownedScheduler;

    private volatile boolean closed;

    WorkerIdLeaseHolder(RedisWorkerLease workerLease, ScheduledExecutorService scheduler,
                        long renewIntervalMillis) {
        this(workerLease, scheduler, renewIntervalMillis, null);
    }

    /**
     * 创建自持续租调度器的持有者，适用于没有共享方的独立场景；关闭时一并销毁调度器。
     */
    static WorkerIdLeaseHolder withOwnScheduler(RedisWorkerLease workerLease, long renewIntervalMillis) {
        ScheduledExecutorService scheduler = RenewalScheduler.singleDaemonThread();
        return new WorkerIdLeaseHolder(workerLease, scheduler, renewIntervalMillis, scheduler);
    }

    private WorkerIdLeaseHolder(RedisWorkerLease workerLease, ScheduledExecutorService scheduler,
                                long renewIntervalMillis, ScheduledExecutorService ownedScheduler) {
        this.workerLease = Objects.requireNonNull(workerLease, "Worker 租约不能为空");
        Objects.requireNonNull(scheduler, "续租调度器不能为空");
        this.ownedScheduler = ownedScheduler;
        this.renewalTask = scheduler.scheduleWithFixedDelay(
                workerLease::renew, renewIntervalMillis, renewIntervalMillis, TimeUnit.MILLISECONDS);
    }

    int workerId() {
        return workerLease.workerId();
    }

    /**
     * 把一个生成器绑到本租约上，返回的实例在租约失效或关闭后拒绝出号。
     */
    GlobalIdGenerator bind(GlobalIdGenerator delegate) {
        return new LeaseBackedGlobalIdGenerator(this, Objects.requireNonNull(delegate, "ID 生成器不能为空"));
    }

    /**
     * 在读锁保护下委派出号。
     */
    long nextId(GlobalIdGenerator delegate) {
        lifecycleLock.readLock().lock();
        try {
            if (closed || !workerLease.isValid()) {
                throw new IdGenerationException("Worker ID 租约已失效，拒绝生成 ID");
            }
            try {
                return delegate.nextId();
            } catch (RuntimeException exception) {
                throw new IdGenerationException("生成 ID 失败", exception);
            }
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    @Override
    public void close() {
        lifecycleLock.writeLock().lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            renewalTask.cancel(false);
        } finally {
            lifecycleLock.writeLock().unlock();
        }
        // 释放租约包含 Redis 调用，放在写锁外：此时 closed 已对所有出号线程可见，不会再有号发出。
        workerLease.close();
        if (ownedScheduler != null) {
            ownedScheduler.shutdownNow();
        }
    }
}
