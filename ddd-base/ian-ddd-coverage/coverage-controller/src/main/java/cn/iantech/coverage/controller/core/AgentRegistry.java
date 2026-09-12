package cn.iantech.coverage.controller.core;

import cn.iantech.coverage.controller.config.CoverageProperties;

import java.util.*;

/**
 * 覆盖率 Agent 注册表：维护 Agent 名称到 TCP 地址的映射，以及报告所需的类/源码目录。
 *
 * <p>注册表由两层叠加而成：</p>
 * <ol>
 *     <li><b>基线层</b>：来自 {@code application.yml} 的 {@code coverage.agents} 与 {@code coverage.reports}；</li>
 *     <li><b>运行时层</b>：通过注册接口动态添加的服务，同名时覆盖基线层。</li>
 * </ol>
 *
 * <p>基线层的条目不能被注销；注销只能移除运行时层，避免控制器状态与配置文件不一致。</p>
 */
public class AgentRegistry {

    private final Map<String, CoverageProperties.Agent> configuredAgents = new LinkedHashMap<>();
    private final Map<String, CoverageProperties.ServiceReport> configuredReports = new LinkedHashMap<>();
    private final Map<String, CoverageProperties.Agent> registeredAgents = new LinkedHashMap<>();
    private final Map<String, CoverageProperties.ServiceReport> registeredReports = new LinkedHashMap<>();

    public AgentRegistry(CoverageProperties properties) {
        for (CoverageProperties.Agent agent : properties.getAgents()) {
            String name = trimToNull(agent.getName());
            if (name == null) {
                throw new IllegalStateException("coverage.agents 中存在未配置 name 的 Agent");
            }
            if (configuredAgents.put(name, agent) != null) {
                throw new IllegalStateException("coverage.agents 中存在重复的 Agent name: " + name);
            }
        }
        for (CoverageProperties.ServiceReport report : properties.getReports()) {
            String name = trimToNull(report.getName());
            if (name == null) {
                throw new IllegalStateException("coverage.reports 中存在未配置 name 的服务");
            }
            configuredReports.put(name, report);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 注册（或覆盖）一个运行时服务：Agent 地址与报告目录同时生效。
     */
    public synchronized void register(CoverageProperties.Agent agent, CoverageProperties.ServiceReport report) {
        registeredAgents.put(agent.getName(), agent);
        registeredReports.put(report.getName(), report);
    }

    /**
     * 注销运行时注册的服务。
     *
     * @return 是否确实移除了运行时条目；基线层条目返回 false
     */
    public synchronized boolean unregister(String name) {
        boolean removed = registeredReports.remove(name) != null;
        removed |= registeredAgents.remove(name) != null;
        return removed;
    }

    /**
     * 该名称是否来自配置文件（不可注销）。
     */
    public synchronized boolean isConfigured(String name) {
        return configuredAgents.containsKey(name) || configuredReports.containsKey(name);
    }

    /**
     * 该名称是否来自运行时注册接口。
     */
    public synchronized boolean isRuntimeRegistered(String name) {
        return registeredAgents.containsKey(name) || registeredReports.containsKey(name);
    }

    public synchronized List<CoverageProperties.Agent> allAgents() {
        return List.copyOf(mergeAgents(configuredAgents, registeredAgents).values());
    }

    public synchronized Optional<CoverageProperties.Agent> findAgent(String name) {
        return Optional.ofNullable(mergeAgents(configuredAgents, registeredAgents).get(name));
    }

    public synchronized List<CoverageProperties.ServiceReport> allReports() {
        return List.copyOf(mergeReports(configuredReports, registeredReports).values());
    }

    public synchronized Optional<CoverageProperties.ServiceReport> findReport(String name) {
        return Optional.ofNullable(mergeReports(configuredReports, registeredReports).get(name));
    }

    /**
     * 按名称解析 Agent 列表；names 为空时返回全部注册 Agent。
     */
    public synchronized List<CoverageProperties.Agent> resolve(List<String> names) {
        if (names == null || names.isEmpty()) {
            return allAgents();
        }
        Map<String, CoverageProperties.Agent> merged = mergeAgents(configuredAgents, registeredAgents);
        List<CoverageProperties.Agent> resolved = new ArrayList<>();
        for (String name : names) {
            CoverageProperties.Agent agent = merged.get(name);
            if (agent == null) {
                throw new IllegalArgumentException("未注册的覆盖率 Agent: " + name);
            }
            resolved.add(agent);
        }
        return List.copyOf(resolved);
    }

    /**
     * 返回当前生效的注册表快照，供一次会话流程内部使用，避免执行期间被并发变更影响。
     */
    public synchronized Snapshot snapshot() {
        return new Snapshot(mergeAgents(configuredAgents, registeredAgents),
                mergeReports(configuredReports, registeredReports));
    }

    private Map<String, CoverageProperties.Agent> mergeAgents(Map<String, CoverageProperties.Agent> base,
                                                              Map<String, CoverageProperties.Agent> overlay) {
        Map<String, CoverageProperties.Agent> merged = new LinkedHashMap<>(base);
        merged.putAll(overlay);
        return merged;
    }

    private Map<String, CoverageProperties.ServiceReport> mergeReports(
            Map<String, CoverageProperties.ServiceReport> base,
            Map<String, CoverageProperties.ServiceReport> overlay) {
        Map<String, CoverageProperties.ServiceReport> merged = new LinkedHashMap<>(base);
        merged.putAll(overlay);
        return merged;
    }

    /**
     * 注册表的一次性快照，用于保证单个会话流程内看到的配置一致。
     */
    public record Snapshot(Map<String, CoverageProperties.Agent> agents,
                           Map<String, CoverageProperties.ServiceReport> reports) {

        public List<CoverageProperties.Agent> agentList() {
            return List.copyOf(agents.values());
        }

        public List<CoverageProperties.ServiceReport> reportList() {
            return List.copyOf(reports.values());
        }
    }
}
