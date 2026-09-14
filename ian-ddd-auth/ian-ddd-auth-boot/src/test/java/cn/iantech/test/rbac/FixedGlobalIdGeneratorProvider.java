package cn.iantech.test.rbac;

import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.id.GlobalIdGeneratorProvider;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 测试替身：所有业务共用一份确定性的自增 ID，避免用例依赖 Redis 租约。
 */
final class FixedGlobalIdGeneratorProvider implements GlobalIdGeneratorProvider {

    private final GlobalIdGenerator generator;

    FixedGlobalIdGeneratorProvider() {
        AtomicLong sequence = new AtomicLong(1_000_000L);
        this.generator = sequence::incrementAndGet;
    }

    @Override
    public GlobalIdGenerator forBusiness(String business) {
        return generator;
    }

    @Override
    public void close() {
    }
}
