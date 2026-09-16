package cn.iantech.id;

/**
 * 按业务获取 ID 生成器。
 *
 * <p>每个已声明的业务各有一份生成器实例（独立锁、独立序列、独立序列位宽），
 * 但**全部业务共用同一个实例级 Worker ID 与同一条租约**，因此租约失效会同时停掉它们。
 *
 * <p>唯一性保证是「业务域内唯一」：因为共用 Worker ID 与序列起点，不同业务在同一毫秒内会产出
 * 相同的 ID 序列，跨业务表允许数值相同。会流入同一个字段、同一列或同一个 Redis 作用域的 ID，
 * 必须由同一个业务产出。
 */
public interface GlobalIdGeneratorProvider extends AutoCloseable {

    /**
     * 获取指定业务的 ID 生成器，同一业务始终返回同一实例。
     *
     * @param business 业务名，必须已在 {@code ddd.id-generator.businesses} 中声明
     * @return 该业务独占一份序列的生成器
     * @throws IdGenerationException 业务未声明时抛出
     */
    GlobalIdGenerator forBusiness(String business);

    @Override
    void close();
}
