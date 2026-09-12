package cn.iantech.id;

import com.github.yitter.contract.IIdGenerator;
import com.github.yitter.contract.IdGeneratorOptions;
import com.github.yitter.idgen.DefaultIdGenerator;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 Yitter 算法和 Redis Worker 租约的全局 ID 生成器。
 */
final class YitterGlobalIdGenerator implements GlobalIdGenerator, AutoCloseable {

    private final RedisWorkerLease workerLease;
    private final IIdGenerator delegate;
    private final ScheduledExecutorService renewalExecutor;
    private final AtomicBoolean closed = new AtomicBoolean();

    YitterGlobalIdGenerator(RedisWorkerLease workerLease, IdGeneratorProperties properties) {
        this(workerLease, createDelegate(workerLease, properties), createRenewalExecutor(),
                properties.getRenewInterval().toMillis());
    }

    YitterGlobalIdGenerator(RedisWorkerLease workerLease,
                            IIdGenerator delegate,
                            ScheduledExecutorService renewalExecutor,
                            long renewIntervalMillis) {
        this.workerLease = Objects.requireNonNull(workerLease, "Worker 租约不能为空");
        this.delegate = Objects.requireNonNull(delegate, "Yitter 生成器不能为空");
        this.renewalExecutor = Objects.requireNonNull(renewalExecutor, "续租执行器不能为空");
        this.renewalExecutor.scheduleWithFixedDelay(
                workerLease::renew, renewIntervalMillis, renewIntervalMillis, TimeUnit.MILLISECONDS);
    }

    private static IIdGenerator createDelegate(
            RedisWorkerLease workerLease, IdGeneratorProperties properties) {
        IdGeneratorOptions options = new IdGeneratorOptions((short) workerLease.workerId());
        options.WorkerIdBitLength = (byte) properties.getWorkerIdBitLength();
        options.SeqBitLength = (byte) properties.getSequenceBitLength();
        try {
            return new DefaultIdGenerator(options);
        } catch (RuntimeException exception) {
            workerLease.close();
            throw new IdGenerationException("初始化 Yitter ID 生成器失败", exception);
        }
    }

    private static ScheduledExecutorService createRenewalExecutor() {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "ddd-id-generator-renewal");
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    @Override
    public long nextId() {
        if (closed.get() || !workerLease.isValid()) {
            throw new IdGenerationException("Worker ID 租约已失效，拒绝生成 ID");
        }
        try {
            return delegate.newLong();
        } catch (RuntimeException exception) {
            throw new IdGenerationException("Yitter 生成 ID 失败", exception);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        renewalExecutor.shutdownNow();
        workerLease.close();
    }
}
