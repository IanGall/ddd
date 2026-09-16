package cn.iantech.mysql.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明该持久化对象的主键由 ID 生成器在 insert 时自动填充。
 *
 * <p>打在**类**上而不是 id 字段上：本项目的 id 声明在共享基类 {@code BasePO} 里，
 * 字段级注解无法按表区分；类级注解还能让「这张表用不用生成器」一目了然、可直接 grep。
 *
 * <p>填充由 {@code IdAutoFillInterceptor} 在 MyBatis 执行 insert 前完成，规则是
 * <b>仅在 {@code id} 为 null 时生成</b>：调用方显式传入的 id 不会被覆盖，
 * 因此重试同一个对象也不会重复消耗号段。
 *
 * <p><b>不标注解的表不会被碰</b>，所以自增主键表（含分片表）保持原样即可。
 * 反过来，一旦标注了注解，就要求该表的 INSERT 语句绑定了 id 列——没有绑定时会打
 * 警告并跳过填充（填了也不会落库，只会造成「id 已生成」的假象）。
 *
 * <p><b>适用范围是单库非分片表。</b>分片表必须保留自增主键：随机主键会导致 InnoDB
 * 页分裂与大量随机 I/O（见 {@code user_order} 的列注释）。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IdGenerator {

    /**
     * 业务名，必须与 {@code ddd.id-generator.businesses} 中声明的键一致。
     *
     * <p>留空表示使用单生成器模式下的 {@code GlobalIdGenerator}
     * （适用于未声明 {@code businesses} 的服务）。
     *
     * @return 业务名，或空串表示单生成器模式
     */
    String value() default "";
}
