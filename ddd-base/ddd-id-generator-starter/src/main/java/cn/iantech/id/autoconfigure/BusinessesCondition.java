package cn.iantech.id.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

/**
 * 依据 {@code ddd.id-generator.businesses} 是否配置来决定装配分支。
 *
 * <p>业务映射没有单一属性名（只有 {@code businesses.<name>} 叶子键），
 * 因此不能用 {@code @ConditionalOnProperty}，改用 {@link Binder} 直接绑定该映射。
 * 叶子值是该业务的序列位宽，这里只关心映射是否为空。
 */
abstract class BusinessesCondition extends SpringBootCondition {

    private static final String BUSINESSES_PROPERTY = "ddd.id-generator.businesses";

    private final boolean configuredExpected;

    BusinessesCondition(boolean configuredExpected) {
        this.configuredExpected = configuredExpected;
    }

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean configured = !bindBusinesses(context.getEnvironment()).isEmpty();
        String reason = "配置项 " + BUSINESSES_PROPERTY + (configured ? " 已配置" : " 未配置");
        return configured == configuredExpected
                ? ConditionOutcome.match(reason)
                : ConditionOutcome.noMatch(reason);
    }

    private static Map<String, Integer> bindBusinesses(Environment environment) {
        return Binder.get(environment)
                .bind(BUSINESSES_PROPERTY, Bindable.mapOf(String.class, Integer.class))
                .orElseGet(Map::of);
    }
}
