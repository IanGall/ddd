package cn.iantech.id;

/**
 * 按业务获取 ID 生成器。
 *
 * <p>每个已声明的业务独占一个 Worker ID 区间与一份生成器实例，因此不同业务的 ID
 * 既互不重复，也不会互相影响租约续期。
 */
public interface GlobalIdGeneratorProvider extends AutoCloseable {

    /**
     * 获取指定业务的 ID 生成器，同一业务始终返回同一实例。
     *
     * @param business 业务名，必须已在 {@code ddd.id-generator.businesses} 中声明
     * @return 该业务独占 Worker ID 区间的生成器
     * @throws IdGenerationException 业务未声明时抛出
     */
    GlobalIdGenerator forBusiness(String business);

    @Override
    void close();
}
