package cn.iantech.gateway.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * gateway-core 的边界守护：
 * <ul>
 *   <li>core 是可复用组件（被 gateway-app 与骨架工程共同依赖），禁止依赖接入工程的业务包；</li>
 *   <li>组件统一由 {@code GatewayCoreAutoConfiguration} 以自动装配注册，禁止组件扫描注解，
 *       否则与自动装配重复注册或在不同包名的接入工程中失效。</li>
 * </ul>
 */
class GatewayCoreArchitectureTest {

    private static final List<String> FORBIDDEN_IMPORTS = List.of(
            "cn.iantech.gateway.controller.",
            "cn.iantech.gateway.model.",
            "cn.iantech.gateway.service.");

    private static final List<String> FORBIDDEN_ANNOTATIONS = List.of("@Component", "@Service", "@Repository");

    @Test
    void shouldNotDependOnApplicationSpecificPackages() throws IOException {
        List<String> violations = sourceFiles()
                .flatMap(this::linesWithSource)
                .filter(line -> line.startsWith("import "))
                .filter(line -> FORBIDDEN_IMPORTS.stream().anyMatch(line::contains))
                .toList();

        Assertions.assertTrue(violations.isEmpty(),
                () -> "gateway-core 不得依赖接入工程业务包: " + violations);
    }

    @Test
    void shouldRegisterComponentsThroughAutoConfigurationOnly() throws IOException {
        List<String> violations = sourceFiles()
                .flatMap(this::linesWithSource)
                .filter(line -> FORBIDDEN_ANNOTATIONS.stream().anyMatch(line::contains))
                .toList();

        Assertions.assertTrue(violations.isEmpty(),
                () -> "gateway-core 组件必须经 GatewayCoreAutoConfiguration 注册，禁止组件扫描注解: " + violations);
    }

    private Stream<Path> sourceFiles() throws IOException {
        return Files.walk(coreSourceRoot()).filter(path -> path.toString().endsWith(".java"));
    }

    private Stream<String> linesWithSource(Path source) {
        try {
            return Files.readAllLines(source).stream()
                    .map(String::trim)
                    .map(line -> source.getFileName() + ": " + line);
        } catch (IOException exception) {
            throw new IllegalStateException("读取 gateway-core 源码失败: " + source, exception);
        }
    }

    private Path coreSourceRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        return Stream.iterate(current, Objects::nonNull, Path::getParent)
                .map(path -> path.resolve("src/main/java/cn/iantech/gateway/core"))
                .filter(Files::isDirectory)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("无法定位 gateway-core 源码目录: " + current));
    }
}
