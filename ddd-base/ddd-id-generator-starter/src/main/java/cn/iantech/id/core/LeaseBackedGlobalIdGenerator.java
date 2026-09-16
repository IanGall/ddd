package cn.iantech.id.core;

import cn.iantech.id.GlobalIdGenerator;

import java.util.Objects;

/**
 * 绑定到实例级 Worker ID 租约的出号入口。
 *
 * <p>生命周期（续租、关闭、租约释放）由 {@link WorkerIdLeaseHolder} 统一持有，
 * 本类只负责把「租约」与「具体生成器」组合起来。同一个租约上可以绑定多个本类实例
 * （每个业务一个），它们各自持有独立的锁与序列状态，因此互不阻塞。
 */
final class LeaseBackedGlobalIdGenerator implements GlobalIdGenerator, AutoCloseable {

    private final WorkerIdLeaseHolder leaseHolder;
    private final GlobalIdGenerator delegate;

    LeaseBackedGlobalIdGenerator(WorkerIdLeaseHolder leaseHolder, GlobalIdGenerator delegate) {
        this.leaseHolder = Objects.requireNonNull(leaseHolder, "Worker 租约持有者不能为空");
        this.delegate = Objects.requireNonNull(delegate, "ID 生成器不能为空");
    }

    @Override
    public long nextId() {
        return leaseHolder.nextId(delegate);
    }

    /**
     * 关闭共享的租约持有者。多业务模式下多个实例共享同一个持有者，关闭是幂等的。
     */
    @Override
    public void close() {
        leaseHolder.close();
    }
}
