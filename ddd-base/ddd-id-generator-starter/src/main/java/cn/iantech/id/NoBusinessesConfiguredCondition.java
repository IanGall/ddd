package cn.iantech.id;

/**
 * 仅在未配置 {@code ddd.id-generator.businesses} 时命中，用于注册单个全局生成器。
 */
final class NoBusinessesConfiguredCondition extends BusinessesCondition {

    NoBusinessesConfiguredCondition() {
        super(false);
    }
}
