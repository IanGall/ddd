package cn.iantech.id;

/**
 * 全局唯一 ID 生成器。
 */
@FunctionalInterface
public interface GlobalIdGenerator {

    /**
     * 生成一个全局唯一的 64 位整数 ID。
     *
     * @return 全局唯一 ID
     * @throws IdGenerationException 当前实例没有有效 Worker 租约时抛出
     */
    long nextId();
}
