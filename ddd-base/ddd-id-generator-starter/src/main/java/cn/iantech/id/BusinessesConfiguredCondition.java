package cn.iantech.id;

/**
 * 仅在配置了 {@code ddd.id-generator.businesses} 时命中，用于注册按业务划分的生成器 Provider。
 */
final class BusinessesConfiguredCondition extends BusinessesCondition {

    BusinessesConfiguredCondition() {
        super(true);
    }
}
