package cn.iantech.mysql.interceptor;

import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.id.IdGenerationException;
import cn.iantech.mysql.annotation.IdGenerator;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 在执行 INSERT 前，按实体类上的 {@link IdGenerator} 注解填充主键。
 *
 * <p>三条行为约定：
 * <ol>
 *   <li><b>未注解的类一律不碰</b>，因此自增主键表（含分片表）的既有行为完全不变。</li>
 *   <li><b>仅在 {@code id} 为 null 时生成</b>：调用方显式传入的 id 不被覆盖，重试同一个对象也不会重复消耗号段。</li>
 *   <li>注解了但 INSERT 未绑定 id 列时<b>警告并跳过</b>：这种情况下填进去的值不会落库，
 *       只会在内存里造成「id 已生成」的假象，跳过比填充更诚实，也避免白白消耗号段。</li>
 * </ol>
 *
 * <p>拦截点在 MyBatis 层，位于 JDBC 驱动之上，因此在 ShardingSphere 驱动下同样有效：
 * 填充发生在路由与 SQL 改写之前，且分片表按 user_id 之类的业务键路由，与 id 无关。
 *
 * <p>本类不依赖 Spring 类型；生成器的解析由自动装配注入的 {@code generators} 完成
 * （入参是注解上的业务名，空串表示单生成器模式），返回 null 表示容器中没有可用生成器。
 */
@Intercepts(@Signature(type = Executor.class, method = "update",
        args = {MappedStatement.class, Object.class}))
public class IdAutoFillInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(IdAutoFillInterceptor.class);

    /** 约定：主键属性名固定为 id，与各 mapper 的 resultMap 一致。 */
    private static final String ID_PROPERTY = "id";

    /**
     * 类 → 注解上声明的业务名。null 表示该类没有 {@code @IdGenerator}；
     * 空串表示注解了但未指定业务名（走单生成器模式）。两者必须区分。
     *
     * <p>用 {@link ClassValue} 而非普通 Map：随类卸载回收，不会造成 ClassLoader 泄漏。
     */
    private final ClassValue<String> declaredBusiness = new ClassValue<>() {
        @Override
        protected String computeValue(Class<?> type) {
            IdGenerator annotation = type.getAnnotation(IdGenerator.class);
            return annotation == null ? null : annotation.value();
        }
    };

    private final Function<String, GlobalIdGenerator> generators;

    /**
     * @param generators 按业务名解析生成器；返回 null 表示容器中没有可用生成器
     */
    public IdAutoFillInterceptor(Function<String, GlobalIdGenerator> generators) {
        this.generators = Objects.requireNonNull(generators, "生成器解析函数不能为空");
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement statement = (MappedStatement) args[0];
        if (statement.getSqlCommandType() == SqlCommandType.INSERT) {
            fillId(statement, args[1]);
        }
        return invocation.proceed();
    }

    private void fillId(MappedStatement statement, Object parameter) {
        List<Object> entities = new ArrayList<>(2);
        collectDeclaredEntities(parameter, entities);
        if (entities.isEmpty()) {
            return;
        }
        if (!bindsIdColumn(statement, parameter)) {
            log.warn("@IdGenerator 已声明但 INSERT 未绑定 id 列，跳过填充（填了也不会落库）：statement={}, 实体={}",
                    statement.getId(), entityNames(entities));
            return;
        }
        entities.forEach(this::fillEntity);
    }

    private void fillEntity(Object entity) {
        Class<?> entityClass = entity.getClass();
        MetaObject metaObject = SystemMetaObject.forObject(entity);
        if (!metaObject.hasSetter(ID_PROPERTY)) {
            log.warn("{} 标注了 @IdGenerator 但没有 id 属性，跳过填充", entityClass.getName());
            return;
        }
        if (metaObject.getValue(ID_PROPERTY) != null) {
            return;
        }
        metaObject.setValue(ID_PROPERTY, generatorFor(entityClass).nextId());
    }

    /**
     * 收集参数里所有声明了 {@code @IdGenerator} 的实体：支持直接传 PO、
     * {@code Collection}/{@code 数组}（批量），以及 {@code @Param} 包出来的 {@code ParamMap}。
     */
    private void collectDeclaredEntities(Object candidate, List<Object> target) {
        if (candidate == null) {
            return;
        }
        if (candidate instanceof Map<?, ?> map) {
            map.values().forEach(value -> collectDeclaredEntities(value, target));
            return;
        }
        if (candidate instanceof Collection<?> collection) {
            collection.forEach(element -> collectDeclaredEntities(element, target));
            return;
        }
        if (candidate.getClass().isArray()) {
            int length = Array.getLength(candidate);
            for (int index = 0; index < length; index++) {
                collectDeclaredEntities(Array.get(candidate, index), target);
            }
            return;
        }
        if (declaredBusiness.get(candidate.getClass()) != null) {
            target.add(candidate);
        }
    }

    /**
     * INSERT 是否真的绑定了 id 列。批量 {@code <foreach>} 的映射属性形如
     * {@code __frch_item_0.id}，因此后缀匹配而非全等。
     */
    private boolean bindsIdColumn(MappedStatement statement, Object parameter) {
        BoundSql boundSql = statement.getBoundSql(parameter);
        for (ParameterMapping mapping : boundSql.getParameterMappings()) {
            String property = mapping.getProperty();
            if (property != null && (ID_PROPERTY.equals(property) || property.endsWith("." + ID_PROPERTY))) {
                return true;
            }
        }
        return false;
    }

    private GlobalIdGenerator generatorFor(Class<?> entityClass) {
        String business = declaredBusiness.get(entityClass);
        GlobalIdGenerator generator = generators.apply(business);
        if (generator == null) {
            throw new IdGenerationException(entityClass.getName() + " 标注了 @IdGenerator"
                    + (business == null || business.isEmpty()
                    ? "（未指定业务名，需要单生成器模式）"
                    : "(\"" + business + "\"，该业务需已在 ddd.id-generator.businesses 中声明)")
                    + "，但容器中没有可用的 ID 生成器；请确认 ddd.id-generator.enabled 未关闭且配置合法");
        }
        return generator;
    }

    private String entityNames(List<Object> entities) {
        return entities.stream()
                .map(entity -> entity.getClass().getSimpleName())
                .distinct()
                .collect(Collectors.joining(",", "[", "]"));
    }
}
