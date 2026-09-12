package cn.iantech.test.nplusone;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 开启 N+1 观测：统计测试执行期间真实发生的 SQL 查询与远程调用，超出预算即断言失败。
 *
 * <p>可用于测试类（作用于类内所有用例）或单个测试方法（覆盖类级配置）。判定依据是
 * 「同一条归一化 SQL 的重复执行次数」与「同一个远程操作的重复调用次数」——这正是 N+1
 * 的典型特征：参数不同但语句相同，逐元素发起。</p>
 *
 * <pre>{@code
 * @Test
 * @DetectNPlusOne(maxSelects = 6)
 * void shouldQueryUserWithinBudget() {
 *     rbacService.queryUserById(userId);
 * }
 * }</pre>
 *
 * <p>SQL 计数依赖 {@code datasource-proxy} 包装的数据源，由 {@code DddTestAutoConfiguration}
 * 自动装配；远程调用计数依赖 Dubbo consumer filter。未启用数据源或未经过 Dubbo 时对应通道
 * 恒为零。</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(NPlusOneExtension.class)
public @interface DetectNPlusOne {

    /**
     * 单次测试允许执行的 SELECT 总数上限，默认不限制。
     */
    int maxSelects() default Integer.MAX_VALUE;

    /**
     * 同一条归一化 SQL 允许重复执行的次数上限，超过即判定为 N+1。
     */
    int maxRepeatedSelects() default 1;

    /**
     * 单次测试允许发起的远程调用总数上限，默认不限制。
     */
    int maxRemoteCalls() default Integer.MAX_VALUE;

    /**
     * 同一个远程操作允许重复调用的次数上限，超过即判定为 N+1。
     */
    int maxRepeatedRemoteCalls() default 1;

    /**
     * 归一化后的 SQL 命中任一正则（全匹配）时，不计入统计。
     */
    String[] ignoredSqlPatterns() default {};

    /**
     * 命中任一字符串的远程操作（{@code 接口全限定名#方法名}）不计入统计。
     */
    String[] ignoredRemoteOperations() default {};
}
