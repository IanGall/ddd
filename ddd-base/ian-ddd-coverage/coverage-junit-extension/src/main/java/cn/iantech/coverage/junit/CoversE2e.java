package cn.iantech.coverage.junit;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.*;

/**
 * 标记一个端到端测试类需要采集分布式覆盖率。
 *
 * <p>标注后由 {@link CoversE2eExtension} 自动完成：</p>
 * <pre>
 * beforeAll  → 创建 Session、reset 所有（或指定）Agent
 * afterEach  → dump 所有 Agent 并累加 exec
 * afterAll   → merge + 生成报告，并打印整体覆盖率
 * </pre>
 *
 * <p>控制器地址与 Agent 过滤规则可以通过 {@code covers-e2e.properties}、
 * 系统属性或环境变量配置，详见 {@link CoverageExtensionConfig}。</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(CoversE2eExtension.class)
public @interface CoversE2e {

    /**
     * Session 名称，默认取测试类全限定名。
     */
    String value() default "";

    /**
     * 参与采集的 Agent 名称；为空表示全部注册 Agent。
     */
    String[] agents() default {};

    /**
     * 测试构建标识，用于在报告里区分同一套服务的不同构建版本。
     */
    String buildId() default "";
}
