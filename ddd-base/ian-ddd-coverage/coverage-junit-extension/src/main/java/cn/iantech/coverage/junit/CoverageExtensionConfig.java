package cn.iantech.coverage.junit;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 测试侧配置读取。
 *
 * <p>优先级：系统属性 → 环境变量 → {@code covers-e2e.properties} 文件 → 默认值。</p>
 *
 * <table>
 *     <caption>支持的配置项</caption>
 *     <tr><th>键</th><th>默认值</th><th>说明</th></tr>
 *     <tr><td>coverage.controller.url</td><td>http://127.0.0.1:8099</td><td>覆盖率控制器地址</td></tr>
 *     <tr><td>coverage.agents</td><td>（空）</td><td>参与采集的 Agent，逗号分隔；为空表示全部</td></tr>
 *     <tr><td>coverage.enabled</td><td>true</td><td>设为 false 可整体关闭采集</td></tr>
 *     <tr><td>coverage.fail-on-error</td><td>false</td><td>控制器不可用等异常是否让测试失败</td></tr>
 *     <tr><td>coverage.require-reachable-agents</td><td>true</td><td>存在不可达 Agent 时是否直接失败</td></tr>
 * </table>
 */
final class CoverageExtensionConfig {

    private static final String PROPERTIES_FILE = "covers-e2e.properties";

    private static final String DEFAULT_CONTROLLER_URL = "http://127.0.0.1:8099";

    private final Properties fileProperties = new Properties();

    private CoverageExtensionConfig() {
    }

    static CoverageExtensionConfig load() {
        CoverageExtensionConfig config = new CoverageExtensionConfig();
        Path file = Path.of(PROPERTIES_FILE);
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                config.fileProperties.load(reader);
            } catch (IOException e) {
                System.err.println("[coverage] 读取 " + PROPERTIES_FILE + " 失败，使用默认配置: " + e.getMessage());
            }
        } else {
            // 兜底从 classpath 读取，便于打包进测试资源
            try (InputStream input = CoverageExtensionConfig.class.getClassLoader()
                    .getResourceAsStream(PROPERTIES_FILE)) {
                if (input != null) {
                    config.fileProperties.load(input);
                }
            } catch (IOException e) {
                System.err.println("[coverage] 读取 classpath:" + PROPERTIES_FILE + " 失败: " + e.getMessage());
            }
        }
        return config;
    }

    String controllerUrl() {
        return resolve("coverage.controller.url", "COVERAGE_CONTROLLER_URL", DEFAULT_CONTROLLER_URL);
    }

    boolean enabled() {
        return Boolean.parseBoolean(resolve("coverage.enabled", "COVERAGE_ENABLED", "true"));
    }

    boolean failOnError() {
        return Boolean.parseBoolean(resolve("coverage.fail-on-error", "COVERAGE_FAIL_ON_ERROR", "false"));
    }

    boolean requireReachableAgents() {
        return Boolean.parseBoolean(
                resolve("coverage.require-reachable-agents", "COVERAGE_REQUIRE_REACHABLE_AGENTS", "true"));
    }

    /**
     * 注解上声明的 Agent 优先，其次读取配置，最后为空（表示全部 Agent）。
     */
    List<String> agentNames() {
        String raw = System.getProperty("coverage.agents");
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("COVERAGE_AGENTS");
        }
        if (raw == null || raw.isBlank()) {
            raw = fileProperties.getProperty("coverage.agents", "");
        }
        if (raw.isBlank()) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .forEach(names::add);
        return List.copyOf(names);
    }

    private String resolve(String key, String environmentKey, String defaultValue) {
        String systemValue = System.getProperty(key);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue.trim();
        }
        String environmentValue = System.getenv(environmentKey);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue.trim();
        }
        return fileProperties.getProperty(key, defaultValue).trim();
    }
}
