package cn.iantech.id;

/**
 * ID 生成器。
 *
 * <p>保证的是**业务域内唯一**：同一个生成器实例（同一个业务）产出的 ID 互不重复。
 * 不同业务共用同一个实例级 Worker ID 与同一套序列起点，因此跨业务的 ID 可能数值相同，
 * 不能互相比较。
 */
@FunctionalInterface
public interface GlobalIdGenerator {

    /**
     * 生成一个 64 位整数 ID。
     *
     * @return 本业务内唯一、且随时间单调递增的 ID
     * @throws IdGenerationException 当前实例没有有效 Worker 租约时抛出
     */
    long nextId();
}
