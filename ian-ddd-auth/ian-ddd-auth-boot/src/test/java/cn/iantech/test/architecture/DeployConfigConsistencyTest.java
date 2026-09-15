package cn.iantech.test.architecture;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 部署配置护栏：钉住两类「配置写错了但构建照样过、只有真跑才炸」的问题。
 *
 * <p>放置位置：与 {@link TriggerArchitectureTest} 一致，架构/规约类护栏统一放 {@code cn.iantech.test.architecture}。</p>
 */
class DeployConfigConsistencyTest {

    /** 分片配置在 classpath 下的目录（boot 模块的 src/main/resources/sharding）。 */
    private static final String SHARDING_DIR = "sharding/";

    /** 仓库内 k8s 清单目录（相对 boot 模块）。 */
    private static final Path K8S_DIR = Path.of("..", "docs", "dev-ops", "k8s");

    /** 会参与环境变量注入的配置文件：多 profile 一起看，避免只在某一个 profile 里漏配。 */
    private static final List<String> CONFIG_FILES = List.of(
            "application.yml", "application-dev.yml", "application-prod.yml",
            SHARDING_DIR + "sharding-jdbc-dev.yaml", SHARDING_DIR + "sharding-jdbc-prod.yaml");

    private static final List<String> SHARDING_FILES = List.of(
            SHARDING_DIR + "sharding-jdbc-dev.yaml",
            SHARDING_DIR + "sharding-jdbc-prod.yaml",
            SHARDING_DIR + "sharding-jdbc-autotest.yaml");

    /**
     * 显式例外：这些键只在特定开关打开时才需要解析，因此可以不进默认 ConfigMap。
     * 例外必须写明原因，不要为了「让测试通过」而扩大这张表。
     */
    private static final Set<String> ALLOWED_MISSING = Set.of(
            // 仅 XXL_JOB_ENABLED=true 时才创建执行器 Bean（见 XxlJobAutoConfig 的 @ConditionalOnProperty）
            "XXL_JOB_ADMIN_ADDRESSES");

    /**
     * ShardingSphere 只识别带双冒号的占位符（URLArgumentLine 的 PLACEHOLDER_PATTERN = \$\$\{(.*?)::(.*?)}）。
     * 少了双冒号不会被匹配，占位符会原样留在 YAML 里，表现是「环境变量怎么改都不生效」——2026-09-15 的
     * BE-56 就是这么来的（为了不给可用默认值而删掉 `::`，副作用是整个占位符失效）。
     */
    @Test
    void shardingPlaceholdersMustUseDoubleColonSyntax() throws Exception {
        Pattern placeholder = Pattern.compile("\\$\\$\\{([^}]*)}");
        List<String> violations = new ArrayList<>();
        for (String file : SHARDING_FILES) {
            for (String line : readClasspathLines(file)) {
                Matcher matcher = placeholder.matcher(codePart(line));
                while (matcher.find()) {
                    if (!matcher.group(1).contains("::")) {
                        violations.add(file + " 的 " + matcher.group() + " 缺少双冒号");
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "以下分片配置占位符缺少双冒号（写成 $${VAR::默认值}），"
                + "ShardingSphere 不会替换它们：" + System.lineSeparator() + String.join(System.lineSeparator(), violations));
    }

    /**
     * prod/dev 配置里引用的环境变量，必须能在已提交的 k8s ConfigMap 或 Secret 示例里找到。
     * 2026-09-15 就出现过一次「把骨架模板的 NACOS_* 当成认证服务的键名」，而真实服务用的是
     * DUBBO_REGISTRY_*，整套注入因此全部落空、Pod 起不来。
     */
    @Test
    void deployManifestsMustProvideEveryReferencedEnvKey() throws Exception {
        Set<String> referenced = new LinkedHashSet<>();
        for (String file : CONFIG_FILES) {
            referenced.addAll(envKeysIn(readClasspathLines(file)));
        }
        Set<String> provided = new LinkedHashSet<>();
        provided.addAll(declaredKeysIn(K8S_DIR.resolve("configmap.yaml"), "data"));
        provided.addAll(declaredKeysIn(K8S_DIR.resolve("secret.yaml.example"), "stringData"));

        List<String> missing = referenced.stream()
                .filter(key -> !provided.contains(key))
                .filter(key -> !ALLOWED_MISSING.contains(key))
                .sorted()
                .toList();
        assertTrue(missing.isEmpty(), "以下环境变量在配置里被引用，但 k8s ConfigMap / Secret 示例里没有提供："
                + String.join(", ", missing)
                + System.lineSeparator() + "请把它们补进 " + K8S_DIR + " 的 configmap.yaml（非敏感）或 secret.yaml.example（敏感）；"
                + "确属「仅特定开关下才需要」的，才可加进本测试的 ALLOWED_MISSING 并写明原因");
    }

    /** 去掉行内注释，避免把注释里举例的占位符当成真实配置。 */
    private static String codePart(String line) {
        int index = line.indexOf('#');
        return index < 0 ? line : line.substring(0, index);
    }

    /** 收集 ${VAR...} 与 $${VAR...} 两种写法里的变量名。 */
    private static Set<String> envKeysIn(List<String> lines) {
        Pattern patterns = Pattern.compile("\\$\\$?\\{([A-Za-z_][A-Za-z0-9_]*)");
        Set<String> keys = new LinkedHashSet<>();
        for (String line : lines) {
            Matcher matcher = patterns.matcher(codePart(line));
            while (matcher.find()) {
                keys.add(matcher.group(1));
            }
        }
        return keys;
    }

    /** 取 YAML 顶层块（例如 data / stringData）下缩进两格的键名。 */
    private static Set<String> declaredKeysIn(Path file, String block) throws Exception {
        assertTrue(Files.isRegularFile(file), "缺少 k8s 清单：" + file.toAbsolutePath());
        Set<String> keys = new LinkedHashSet<>();
        boolean inside = false;
        Pattern key = Pattern.compile("^ {2}([A-Za-z_][A-Za-z0-9_]*):.*$");
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.startsWith(block + ":")) {
                inside = true;
                continue;
            }
            if (inside && !line.isBlank() && !line.startsWith(" ")) {
                inside = false;
            }
            if (inside) {
                Matcher matcher = key.matcher(line);
                if (matcher.matches()) {
                    keys.add(matcher.group(1));
                }
            }
        }
        return keys;
    }

    private static List<String> readClasspathLines(String resource) throws Exception {
        try (InputStream stream = DeployConfigConsistencyTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertTrue(stream != null, "classpath 下找不到 " + resource);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines().toList();
            }
        }
    }
}
