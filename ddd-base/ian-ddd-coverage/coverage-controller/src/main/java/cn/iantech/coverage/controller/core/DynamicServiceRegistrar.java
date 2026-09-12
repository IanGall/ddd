package cn.iantech.coverage.controller.core;

import cn.iantech.coverage.controller.config.CoverageProperties;
import cn.iantech.coverage.controller.config.PathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 运行时服务注册：把新服务的 Agent 地址与报告目录登记进 {@link AgentRegistry}，无需改配置重启。
 *
 * <p>每个注册结果同时落盘到 {@code <工作目录>/services/<name>.properties}，
 * 控制器重启时会自动恢复，因此注册一次即可长期生效。</p>
 */
public class DynamicServiceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(DynamicServiceRegistrar.class);

    /**
     * 服务名会用于文件名与 URL，限制字符集避免路径穿越。
     */
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private static final String SERVICES_SUBDIRECTORY = "services";

    private final AgentRegistry registry;

    private final Path servicesDirectory;

    public DynamicServiceRegistrar(AgentRegistry registry, Path workDirectory) {
        this.registry = registry;
        this.servicesDirectory = workDirectory.resolve(SERVICES_SUBDIRECTORY);
    }

    /**
     * 注册一个服务。
     *
     * <p>失败语义：参数非法抛 {@link IllegalArgumentException}（HTTP 400），
     * 与服务注册表冲突抛 {@link IllegalStateException}（HTTP 409）。</p>
     *
     * @param request 注册请求
     */
    public CoverageModels.ServiceView register(CoverageModels.ServiceRegistrationRequest request) {
        validate(request);

        CoverageProperties.Agent agent = new CoverageProperties.Agent();
        agent.setName(request.name());
        agent.setHost(request.host() == null || request.host().isBlank() ? "127.0.0.1" : request.host().trim());
        agent.setPort(request.agentPort());

        CoverageProperties.ServiceReport report = new CoverageProperties.ServiceReport();
        report.setName(request.name());
        report.setClassesDirectories(List.copyOf(request.classesDirectories()));
        report.setSourceDirectories(request.sourceDirectories() == null ? List.of() : List.copyOf(request.sourceDirectories()));

        List<String> warnings = collectWarnings(request);
        registry.register(agent, report);
        persist(agent, report);

        warnings.forEach(warning -> log.warn("服务 [{}] 注册告警: {}", request.name(), warning));
        log.info("服务 [{}] 已注册: Agent {}:{}, 报告目录 {}", request.name(), agent.getHost(), agent.getPort(),
                report.getClassesDirectories());
        return toView(agent, report, true, warnings);
    }

    /**
     * 注销运行时注册的服务；配置文件中的服务不可注销。
     */
    public boolean unregister(String name) {
        if (registry.isConfigured(name)) {
            throw new IllegalArgumentException("服务 [" + name + "] 来自配置文件，不能在运行时注销；请修改 coverage.reports 后重启控制器");
        }
        boolean removed = registry.unregister(name);
        try {
            Files.deleteIfExists(serviceFile(name));
        } catch (IOException e) {
            log.warn("删除注册记录失败: {} -> {}", serviceFile(name), e.getMessage());
        }
        if (removed) {
            log.info("服务 [{}] 已注销", name);
        }
        return removed;
    }

    /**
     * 当前生效的全部服务（含配置文件基线与运行时注册）。
     */
    public List<CoverageModels.ServiceView> list() {
        AgentRegistry.Snapshot snapshot = registry.snapshot();
        List<CoverageModels.ServiceView> views = new ArrayList<>();
        snapshot.agents().forEach((name, agent) -> {
            CoverageProperties.ServiceReport report = snapshot.reports().get(name);
            if (report == null) {
                return;
            }
            List<String> warnings = new ArrayList<>();
            for (String configured : report.getClassesDirectories()) {
                if (!Files.isDirectory(PathResolver.resolve(configured))) {
                    warnings.add("classes 目录不存在: " + configured);
                }
            }
            // 来源以注册表内部状态为准：基线层是配置文件，运行时层是接口注册
            views.add(toView(agent, report, registry.isRuntimeRegistered(name), warnings));
        });
        views.sort(Comparator.comparing(CoverageModels.ServiceView::name));
        return List.copyOf(views);
    }

    /**
     * 启动时恢复已落盘的注册结果。
     *
     * <p>恢复失败（例如 classes 目录已被清理）时只告警，不阻断控制器启动。</p>
     */
    public int restore() {
        if (!Files.isDirectory(servicesDirectory)) {
            return 0;
        }
        int restored = 0;
        try (Stream<Path> files = Files.list(servicesDirectory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".properties")).toList()) {
                try {
                    restoreOne(file);
                    restored++;
                } catch (RuntimeException | IOException e) {
                    log.warn("恢复服务注册失败，已跳过: {} -> {}", file.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("读取服务注册目录失败: {} -> {}", servicesDirectory, e.getMessage());
        }
        if (restored > 0) {
            log.info("已从 {} 恢复 {} 个运行时注册的服务", servicesDirectory, restored);
        }
        return restored;
    }

    private void restoreOne(Path file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        CoverageProperties.Agent agent = new CoverageProperties.Agent();
        agent.setName(properties.getProperty("name", stripExtension(file)));
        agent.setHost(properties.getProperty("agent.host", "127.0.0.1"));
        agent.setPort(parsePort(properties.getProperty("agent.port"), agent.getName()));

        CoverageProperties.ServiceReport report = new CoverageProperties.ServiceReport();
        report.setName(agent.getName());
        report.setClassesDirectories(readList(properties, "report.classesDirectories"));
        report.setSourceDirectories(readList(properties, "report.sourceDirectories"));

        validateName(agent.getName());
        if (report.getClassesDirectories().isEmpty()) {
            throw new IllegalStateException("注册记录缺少 report.classesDirectories");
        }
        registry.register(agent, report);
    }

    private void validate(CoverageModels.ServiceRegistrationRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        validateName(request.name().trim());
        if (request.agentPort() <= 0 || request.agentPort() > 65535) {
            throw new IllegalArgumentException("agentPort 必须在 1~65535 之间");
        }
        if (request.classesDirectories() == null || request.classesDirectories().isEmpty()) {
            throw new IllegalArgumentException("classesDirectories 至少需要一个目录");
        }

        String name = request.name().trim();
        boolean exists = registry.findAgent(name).isPresent() || registry.findReport(name).isPresent();
        if (exists && !request.replace()) {
            throw new IllegalStateException("服务 [" + name + "] 已存在；如需覆盖请设置 replace=true");
        }
    }

    private void validateName(String name) {
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "name 只允许字母、数字、点、下划线、连字符，且长度不超过 64: " + name);
        }
    }

    private List<String> collectWarnings(CoverageModels.ServiceRegistrationRequest request) {
        List<String> warnings = new ArrayList<>();
        for (String configured : request.classesDirectories()) {
            if (configured == null || configured.isBlank()) {
                warnings.add("classes 目录存在空值，已忽略");
                continue;
            }
            if (!Files.isDirectory(PathResolver.resolve(configured))) {
                warnings.add("classes 目录当前不存在（构建后再注册一次，或直接开始采集）: " + configured);
            }
        }
        if (request.sourceDirectories() != null) {
            for (String configured : request.sourceDirectories()) {
                if (configured != null && !configured.isBlank() && !Files.isDirectory(PathResolver.resolve(configured))) {
                    warnings.add("source 目录当前不存在，报告将缺少源码明细: " + configured);
                }
            }
        }
        return warnings;
    }

    private void persist(CoverageProperties.Agent agent, CoverageProperties.ServiceReport report) {
        Properties properties = new Properties();
        properties.setProperty("name", agent.getName());
        properties.setProperty("agent.host", agent.getHost());
        properties.setProperty("agent.port", String.valueOf(agent.getPort()));
        properties.setProperty("report.classesDirectories", String.join(",", report.getClassesDirectories()));
        properties.setProperty("report.sourceDirectories", String.join(",", report.getSourceDirectories()));
        try {
            Files.createDirectories(servicesDirectory);
            try (Writer writer = Files.newBufferedWriter(serviceFile(agent.getName()), StandardCharsets.UTF_8)) {
                properties.store(writer, "由 coverage-controller 注册接口写入；删除本文件即可移除该服务");
            }
        } catch (IOException e) {
            // 落盘失败不影响本次运行，只提示重启后需重新注册
            log.warn("服务 [{}] 注册记录写入失败，控制器重启后需要重新注册: {}", agent.getName(), e.getMessage());
        }
    }

    private CoverageModels.ServiceView toView(CoverageProperties.Agent agent, CoverageProperties.ServiceReport report,
                                              boolean runtimeRegistered, List<String> warnings) {
        return new CoverageModels.ServiceView(agent.getName(), agent.getHost(), agent.getPort(),
                List.copyOf(report.getClassesDirectories()), List.copyOf(report.getSourceDirectories()),
                runtimeRegistered, List.copyOf(warnings));
    }

    private Path serviceFile(String name) {
        return servicesDirectory.resolve(name + ".properties");
    }

    private String stripExtension(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.endsWith(".properties")
                ? fileName.substring(0, fileName.length() - ".properties".length())
                : fileName;
    }

    private int parsePort(String value, String name) {
        try {
            int port = Integer.parseInt(value);
            if (port <= 0 || port > 65535) {
                throw new NumberFormatException("越界");
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("服务 [" + name + "] 的 agent.port 非法: " + value);
        }
    }

    private List<String> readList(Properties properties, String key) {
        String value = properties.getProperty(key, "").trim();
        if (value.isEmpty()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return items;
    }
}
