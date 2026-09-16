package cn.iantech.mysql.interceptor;

import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.id.IdGenerationException;
import cn.iantech.mysql.annotation.IdGenerator;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdAutoFillInterceptorTest {

    private static final long GENERATED_ID = 869643386981388293L;

    private Configuration configuration;
    private List<String> requestedBusinesses;
    private boolean generatorAvailable;
    private long nextId = GENERATED_ID;

    @BeforeEach
    void setUp() {
        configuration = new Configuration();
        requestedBusinesses = new ArrayList<>();
        generatorAvailable = true;
        nextId = GENERATED_ID;
    }

    @Test
    void shouldFillIdForAnnotatedEntity() throws Throwable {
        IdentityPo entity = new IdentityPo();
        ExecutorRecorder recorder = new ExecutorRecorder();

        invokeInsert("insert", idBoundMappings(), entity, recorder);

        assertThat(entity.getId()).isEqualTo(GENERATED_ID);
        assertThat(requestedBusinesses).containsExactly("identity");
        assertThat(recorder.calls).isEqualTo(1);
    }

    @Test
    void shouldFillUsingSingleGeneratorWhenBusinessNameIsEmpty() throws Throwable {
        DefaultBusinessPo entity = new DefaultBusinessPo();

        invokeInsert("insert", idBoundMappings(), entity, new ExecutorRecorder());

        assertThat(entity.getId()).isEqualTo(GENERATED_ID);
        assertThat(requestedBusinesses).containsExactly("");
    }

    @Test
    void shouldNotTouchUnannotatedEntity() throws Throwable {
        PlainPo entity = new PlainPo();

        invokeInsert("insert", idBoundMappings(), entity, new ExecutorRecorder());

        assertThat(entity.getId()).isNull();
        assertThat(requestedBusinesses).isEmpty();
    }

    @Test
    void shouldNotOverwriteProvidedIdAndNotConsumeGenerator() throws Throwable {
        IdentityPo entity = new IdentityPo();
        entity.setId(99L);

        invokeInsert("insert", idBoundMappings(), entity, new ExecutorRecorder());

        assertThat(entity.getId()).isEqualTo(99L);
        assertThat(requestedBusinesses).isEmpty();
    }

    @Test
    void shouldIgnoreNonInsertStatements() throws Throwable {
        IdentityPo entity = new IdentityPo();
        ExecutorRecorder recorder = new ExecutorRecorder();

        MappedStatement statement = statement("update", SqlCommandType.UPDATE, idBoundMappings());
        interceptor().intercept(invocation(statement, entity, recorder));

        assertThat(entity.getId()).isNull();
        assertThat(recorder.calls).isEqualTo(1);
    }

    @Test
    void shouldFillEveryEntityOfCollectionParameterWithDistinctIds() throws Throwable {
        List<IdentityPo> entities = List.of(new IdentityPo(), new IdentityPo());

        invokeInsert("insertBatch", idBoundMappings(), entities, new ExecutorRecorder());

        assertThat(entities).extracting(IdentityPo::getId)
                .containsExactly(GENERATED_ID, GENERATED_ID + 1);
    }

    @Test
    void shouldFillEntityInsideParameterMap() throws Throwable {
        IdentityPo entity = new IdentityPo();
        Map<String, Object> parameters = Map.of("po", entity, "operator", "tester");

        invokeInsert("insert", idBoundMappings(), parameters, new ExecutorRecorder());

        assertThat(entity.getId()).isEqualTo(GENERATED_ID);
    }

    @Test
    void shouldSkipAndStillProceedWhenInsertDoesNotBindIdColumn() throws Throwable {
        IdentityPo entity = new IdentityPo();
        ExecutorRecorder recorder = new ExecutorRecorder();
        List<ParameterMapping> withoutId = List.of(
                new ParameterMapping.Builder(configuration, "username", String.class).build());

        invokeInsert("insert", withoutId, entity, recorder);

        assertThat(entity.getId()).as("未绑定 id 列时填充不会落库，因此不填也不消耗号段").isNull();
        assertThat(requestedBusinesses).isEmpty();
        assertThat(recorder.calls).isEqualTo(1);
    }

    @Test
    void shouldSkipAnnotatedEntityWithoutIdProperty() throws Throwable {
        NoIdPo entity = new NoIdPo();
        ExecutorRecorder recorder = new ExecutorRecorder();

        invokeInsert("insert", idBoundMappings(), entity, recorder);

        assertThat(requestedBusinesses).isEmpty();
        assertThat(recorder.calls).isEqualTo(1);
    }

    @Test
    void shouldFailWhenNoGeneratorIsAvailable() {
        generatorAvailable = false;
        IdentityPo entity = new IdentityPo();

        assertThatThrownBy(() -> invokeInsert("insert", idBoundMappings(), entity, new ExecutorRecorder()))
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining(IdentityPo.class.getName())
                .hasMessageContaining("identity")
                .hasMessageContaining("ddd.id-generator");
    }

    @Test
    void shouldFailWhenSingleGeneratorModeIsNotAvailable() {
        generatorAvailable = false;

        assertThatThrownBy(() -> invokeInsert("insert", idBoundMappings(),
                new DefaultBusinessPo(), new ExecutorRecorder()))
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("未指定业务名");
    }

    private IdAutoFillInterceptor interceptor() {
        return new IdAutoFillInterceptor(business -> {
            requestedBusinesses.add(business);
            return generatorAvailable ? () -> nextId++ : null;
        });
    }

    private void invokeInsert(String statementId, List<ParameterMapping> mappings, Object parameter,
                              ExecutorRecorder recorder) throws Throwable {
        MappedStatement statement = statement(statementId, SqlCommandType.INSERT, mappings);
        interceptor().intercept(invocation(statement, parameter, recorder));
    }

    /**
     * {@link Invocation} 的构造器会校验方法必须声明在 {@link Executor} 等插件目标接口上，
     * 因此目标用动态代理而不是普通类；这样也能顺带统计 {@code proceed()} 是否被调用。
     */
    private Invocation invocation(MappedStatement statement, Object parameter, ExecutorRecorder recorder)
            throws NoSuchMethodException {
        Executor executor = (Executor) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {Executor.class}, recorder);
        Method method = Executor.class.getMethod("update", MappedStatement.class, Object.class);
        return new Invocation(executor, method, new Object[] {statement, parameter});
    }

    private MappedStatement statement(String statementId, SqlCommandType commandType,
                                      List<ParameterMapping> mappings) {
        StaticSqlSource sqlSource = new StaticSqlSource(configuration, "INSERT INTO t (id) VALUES (#{id})",
                mappings);
        return new MappedStatement.Builder(configuration, "test." + statementId + "." + commandType,
                sqlSource, commandType).build();
    }

    private List<ParameterMapping> idBoundMappings() {
        return List.of(new ParameterMapping.Builder(configuration, "id", Long.class).build());
    }

    /** 记录 {@code Executor.update} 被调用的次数。 */
    static final class ExecutorRecorder implements InvocationHandler {

        int calls;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            calls++;
            return 1;
        }
    }

    @IdGenerator("identity")
    static class IdentityPo {

        private Long id;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }

    @IdGenerator
    static class DefaultBusinessPo {

        private Long id;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }

    static class PlainPo {

        private Long id;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }

    @IdGenerator("identity")
    static class NoIdPo {
    }
}
