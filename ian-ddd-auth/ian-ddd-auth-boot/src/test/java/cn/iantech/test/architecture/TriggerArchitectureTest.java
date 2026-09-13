package cn.iantech.test.architecture;

import cn.iantech.trigger.rpc.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

class TriggerArchitectureTest {

    private static final String REMOVED_LAYER_PACKAGE = "cn.iantech." + "application";
    private static final String INFRASTRUCTURE_PACKAGE = "cn.iantech." + "infrastructure";
    private static final List<Class<?>> RPC_ADAPTERS = List.of(AuthService.class, ChannelCredentialService.class,
            CustomerService.class, PlatformAccountService.class, RbacService.class);
    private static final List<String> CASES_FORBIDDEN_IMPORTS = List.of("cn.iantech.api.", "cn.iantech.context.",
            "cn.iantech.trigger.", "cn.iantech.infrastructure.", "org.apache.dubbo.");
    private static final List<String> DOMAIN_FORBIDDEN_IMPORTS = List.of("cn.iantech.cases.",
            "cn.iantech.api.", "cn.iantech.context.", "cn.iantech.trigger.", "cn.iantech.infrastructure.",
            "org.apache.dubbo.", "org.mybatis.", "org.redisson.", "jakarta.servlet.", "javax.servlet.",
            "org.springframework.");
    /**
     * 领域服务默认应为具体类：接口只服务于跨模块端口/SPI（如 infra 契约）。
     * 下列身份认证接口是唯一豁免，理由见 docs/plans/project-optimization-plan.md 阶段 F（测试替身实现该接口）。
     */
    private static final List<String> DOMAIN_SERVICE_INTERFACE_ALLOWLIST = List.of(
            "auth/service/IAdminIdentityAuthenticator.java",
            "auth/service/ICustomerIdentityAuthenticator.java");

    @Test
    void shouldOnlyInjectDomainOrCasesBusinessServicesIntoTrigger() {
        List<Field> invalidFields = RPC_ADAPTERS.stream()
                .flatMap(adapter -> Stream.of(adapter.getDeclaredFields()))
                .filter(this::isInvalidDependency)
                .toList();

        Assertions.assertTrue(invalidFields.isEmpty(), () -> "Trigger 存在越层业务依赖: " + invalidFields);
    }

    @Test
    void shouldKeepDomainDependenciesWithinAllowedBoundaries() throws IOException {
        List<String> invalidImports = findDomainInvalidImports();

        Assertions.assertTrue(invalidImports.isEmpty(),
                () -> "Domain 存在越界依赖或在非领域服务位置使用 Spring: " + invalidImports);
    }

    @Test
    void shouldUseInfraAsTheOnlyInfrastructureContractPackage() throws IOException {
        Path sourceRoot = projectRoot().resolve("ian-ddd-auth-domain/src/main/java/cn/iantech/domain");
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            List<String> obsoletePackages = files.filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> obsoleteInfrastructureContractReferences(path)
                            .map(reference -> path + ": " + reference))
                    .toList();

            Assertions.assertTrue(obsoletePackages.isEmpty(),
                    () -> "Domain 基础设施契约必须统一放入 infra 包: " + obsoletePackages);
        }
    }

    @Test
    void shouldNotDeclareInterfacesForSameModuleDomainServices() throws IOException {
        Path sourceRoot = projectRoot().resolve("ian-ddd-auth-domain/src/main/java/cn/iantech/domain");
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            List<String> serviceInterfaces = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.getFileName().toString().startsWith("I"))
                    .filter(path -> path.toString().contains("/service/"))
                    .map(path -> sourceRoot.relativize(path).toString().replace('\\', '/'))
                    .filter(relative -> !DOMAIN_SERVICE_INTERFACE_ALLOWLIST.contains(relative))
                    .toList();

            Assertions.assertTrue(serviceInterfaces.isEmpty(),
                    () -> "领域服务应为具体类，接口只服务于跨模块端口/SPI；请勿在 service 包新增接口: "
                            + serviceInterfaces);
        }
    }

    private Stream<String> obsoleteInfrastructureContractReferences(Path source) {
        try {
            return Stream.concat(Stream.of(source.toString()), Files.readAllLines(source).stream())
                    .filter(value -> value.contains("/port/")
                            || value.contains("/repository/")
                            || value.contains("/adapter/")
                            || value.matches(".*cn\\.iantech\\.domain\\..*\\.(port|repository|adapter)(\\..*|;).*"));
        } catch (IOException exception) {
            throw new IllegalStateException("读取架构测试源码失败: " + source, exception);
        }
    }

    private List<String> findDomainInvalidImports() throws IOException {
        Path sourceRoot = projectRoot().resolve("ian-ddd-auth-domain/src/main/java/cn/iantech/domain");
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> importsWithSource(path)
                            .map(importLine -> path + ": " + importLine)
                            .filter(importLine -> DOMAIN_FORBIDDEN_IMPORTS.stream()
                                    .anyMatch(importLine::contains))
                            .filter(importLine -> !(path.toString().contains("/service/")
                                    && importLine.contains("org.springframework.stereotype.Service"))))
                    .toList();
        }
    }

    @Test
    void shouldKeepCasesIndependentFromInboundAndInfrastructureLayers() throws IOException {
        List<String> invalidImports = findInvalidImports("cases", CASES_FORBIDDEN_IMPORTS, true);

        Assertions.assertTrue(invalidImports.isEmpty(),
                () -> "Cases 不得依赖 API、Context、RPC、Trigger 或 Infrastructure: " + invalidImports);
    }

    @Test
    void shouldAccessRedisThroughSharedStarter() throws IOException {
        Path infrastructureRoot = projectRoot()
                .resolve("ian-ddd-auth-infrastructure/src/main/java/cn/iantech/infrastructure");
        List<String> directRedissonImports;
        try (Stream<Path> files = Stream.of("auth", "channel")
                .map(infrastructureRoot::resolve)
                .flatMap(this::walkFiles)) {
            directRedissonImports = files.flatMap(this::importsWithSource)
                    .filter(value -> value.contains("org.redisson."))
                    .toList();
        }

        Assertions.assertTrue(directRedissonImports.isEmpty(),
                () -> "Auth/Channel 必须通过公共 Redis Starter 访问 Redis: " + directRedissonImports);
        Assertions.assertFalse(Files.exists(infrastructureRoot.resolve("redis/IRedisService.java")),
                "业务 Infrastructure 不得重复定义 IRedisService");
    }

    private Stream<Path> walkFiles(Path root) {
        try {
            return Files.walk(root).filter(path -> path.toString().endsWith(".java"));
        } catch (IOException exception) {
            throw new IllegalStateException("读取源码目录失败: " + root, exception);
        }
    }

    private boolean isInvalidDependency(Field field) {
        String packageName = field.getType().getPackageName();
        if (packageName.startsWith(REMOVED_LAYER_PACKAGE) || packageName.startsWith(INFRASTRUCTURE_PACKAGE)) {
            return true;
        }
        return field.getType().getSimpleName().endsWith("Service")
                && !packageName.startsWith("cn.iantech.domain")
                && !packageName.startsWith("cn.iantech.cases");
    }

    private List<String> findInvalidImports(String sourcePackage, List<String> forbiddenImports,
                                            boolean forbidRpcPackages) throws IOException {
        Path sourceRoot = projectRoot().resolve("ian-ddd-auth-domain/src/main/java/cn/iantech")
                .resolve(sourcePackage);
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .flatMap(this::importsWithSource)
                    .filter(value -> forbiddenImports.stream().anyMatch(value::contains)
                            || forbidRpcPackages && value.contains(".rpc."))
                    .toList();
        }
    }

    private Stream<String> importsWithSource(Path source) {
        try {
            return Files.readAllLines(source).stream()
                    .map(String::trim)
                    .filter(line -> line.startsWith("import "))
                    .map(line -> source.getFileName() + ": " + line);
        } catch (IOException exception) {
            throw new IllegalStateException("读取架构测试源码失败: " + source, exception);
        }
    }

    private Path projectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        return Stream.iterate(current, Objects::nonNull, Path::getParent)
                .filter(path -> Files.isDirectory(path.resolve("ian-ddd-auth-domain")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("无法定位标准工程根目录: " + current));
    }
}
