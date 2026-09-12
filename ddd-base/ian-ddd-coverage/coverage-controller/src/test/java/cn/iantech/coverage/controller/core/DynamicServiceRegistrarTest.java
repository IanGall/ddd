package cn.iantech.coverage.controller.core;

import cn.iantech.coverage.controller.WorkDirectory;
import cn.iantech.coverage.controller.config.CoverageProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 运行时服务注册：注册即生效、落盘可恢复、配置来源不可注销。
 *
 * <p>每个用例使用独立的工作目录，避免落盘的注册记录相互污染。</p>
 */
class DynamicServiceRegistrarTest {

    @TempDir
    Path tempDir;

    private Path classesDirectory;

    private AgentRegistry registry;

    private DynamicServiceRegistrar registrar;

    @BeforeEach
    void setUp() throws Exception {
        WorkDirectory.override(tempDir);

        classesDirectory = Files.createDirectories(tempDir.resolve("order-classes"));
        Files.createFile(classesDirectory.resolve("OrderService.class"));

        registry = new AgentRegistry(configuredProperties());
        registrar = new DynamicServiceRegistrar(registry, tempDir);
    }

    @AfterEach
    void tearDown() {
        WorkDirectory.reset();
    }

    @Test
    void shouldRegisterServiceAndExposeItToTheSessionFlow() {
        CoverageModels.ServiceView view = registrar.register(request("order", 6302, false));

        assertEquals("order", view.name());
        assertEquals(6302, view.agentPort());
        assertTrue(view.runtimeRegistered(), "运行时注册的服务应标记来源");
        assertFalse(registry.findAgent("order").isEmpty(), "注册后 Agent 应立即可用");
        assertFalse(registry.findReport("order").isEmpty(), "注册后报告配置应立即可用");
    }

    @Test
    void shouldPersistRegistrationSoRestartCanRestore() {
        registrar.register(request("pay", 6303, false));

        // 模拟控制器重启：用同一工作目录新建注册表与注册器
        CoverageProperties empty = new CoverageProperties();
        AgentRegistry restoredRegistry = new AgentRegistry(empty);
        DynamicServiceRegistrar restored = new DynamicServiceRegistrar(restoredRegistry, tempDir);

        assertEquals(1, restored.restore(), "应恢复 1 个已落盘的服务");
        assertEquals(6303, restoredRegistry.findAgent("pay").orElseThrow().getPort());
        assertEquals(List.of(classesDirectory.toString()),
                restoredRegistry.findReport("pay").orElseThrow().getClassesDirectories());
    }

    @Test
    void shouldRejectDuplicateUnlessReplaceRequested() {
        registrar.register(request("cart", 6304, false));

        IllegalStateException conflict = assertThrows(IllegalStateException.class,
                () -> registrar.register(request("cart", 6305, false)));
        assertTrue(conflict.getMessage().contains("replace=true"), "冲突提示应给出覆盖方式");

        CoverageModels.ServiceView replaced = registrar.register(request("cart", 6305, true));
        assertEquals(6305, replaced.agentPort(), "replace=true 应覆盖端口");
    }

    @Test
    void shouldRejectInvalidNameAndPort() {
        assertThrows(IllegalArgumentException.class,
                () -> registrar.register(new CoverageModels.ServiceRegistrationRequest(
                        "../escape", "127.0.0.1", 6306, List.of(classesDirectory.toString()), List.of(), false)));
        assertThrows(IllegalArgumentException.class,
                () -> registrar.register(new CoverageModels.ServiceRegistrationRequest(
                        "bad-port", "127.0.0.1", 70000, List.of(classesDirectory.toString()), List.of(), false)));
        assertThrows(IllegalArgumentException.class,
                () -> registrar.register(new CoverageModels.ServiceRegistrationRequest(
                        "no-classes", "127.0.0.1", 6307, List.of(), List.of(), false)));
    }

    @Test
    void shouldWarnWhenClassesDirectoryMissing() throws Exception {
        String missing = tempDir.resolve("not-built-yet").toString();
        CoverageModels.ServiceView view = registrar.register(new CoverageModels.ServiceRegistrationRequest(
                "later", "127.0.0.1", 6308, List.of(missing), List.of(), false));

        assertEquals(1, view.warnings().size(), "目录缺失应给出告警而不是拒绝注册");
        assertTrue(view.warnings().get(0).contains("not-built-yet"));
    }

    @Test
    void shouldUnregisterRuntimeServiceButNotConfiguredOne() {
        registrar.register(request("temp", 6309, false));
        assertTrue(registrar.unregister("temp"), "运行时注册的服务应可注销");
        assertTrue(registry.findAgent("temp").isEmpty());
        assertFalse(Files.exists(tempDir.resolve("services/temp.properties")), "注销应同时清理落盘记录");

        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> registrar.unregister("gateway"));
        assertTrue(rejected.getMessage().contains("配置文件"), "配置来源的服务不可注销");
        assertFalse(registry.findAgent("gateway").isEmpty(), "配置来源的服务必须保留");
    }

    @Test
    void shouldListConfiguredAndRuntimeServices() {
        registrar.register(request("listed", 6310, false));

        List<CoverageModels.ServiceView> views = registrar.list();
        assertEquals(2, views.size());
        // 列表按名称排序，配置来源的 gateway 在前
        assertEquals("gateway", views.get(0).name());
        assertFalse(views.get(0).runtimeRegistered(), "配置文件里的服务不应标记为运行时注册");
        assertEquals("listed", views.get(1).name());
        assertTrue(views.get(1).runtimeRegistered(), "接口注册的服务必须标记为运行时注册");
    }

    private CoverageProperties configuredProperties() {
        CoverageProperties properties = new CoverageProperties();
        CoverageProperties.Agent configured = new CoverageProperties.Agent();
        configured.setName("gateway");
        configured.setPort(6300);
        CoverageProperties.ServiceReport report = new CoverageProperties.ServiceReport();
        report.setName("gateway");
        report.setClassesDirectories(List.of(classesDirectory.toString()));
        properties.setAgents(List.of(configured));
        properties.setReports(List.of(report));
        return properties;
    }

    private CoverageModels.ServiceRegistrationRequest request(String name, int port, boolean replace) {
        return new CoverageModels.ServiceRegistrationRequest(name, "127.0.0.1", port,
                List.of(classesDirectory.toString()), List.of(), replace);
    }
}
