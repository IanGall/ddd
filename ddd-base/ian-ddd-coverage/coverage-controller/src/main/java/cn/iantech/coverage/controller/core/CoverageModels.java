package cn.iantech.coverage.controller.core;

import cn.iantech.coverage.controller.config.CoverageProperties;

import java.util.List;

/**
 * 控制器对外暴露的 Session 视图与请求模型。
 */
public final class CoverageModels {

    private CoverageModels() {
    }

    /**
     * 创建 Session 的请求体。
     */
    public record StartSessionRequest(String name, String buildId) {
    }

    /**
     * reset / terminate 等按 Agent 过滤的请求体，agentNames 为空表示全部注册 Agent。
     */
    public record AgentSelectionRequest(List<String> agentNames) {
    }

    /**
     * 单个 Agent 的采集结果。
     */
    public record AgentDumpResult(String agentName, boolean success, String execFile, String message) {
    }

    /**
     * 一次 reset / dump 的整体结果。
     */
    public record DumpReport(String sessionId, int successCount, int failedCount, List<AgentDumpResult> results) {
    }

    /**
     * Agent 连通性探测结果，用于在测试开始前给出明确提示。
     */
    public record AgentAvailability(String agentName, boolean reachable, String message) {
    }

    /**
     * Session 视图。
     *
     * @param directory 会话产物目录，以 file URI 形式返回，便于跨平台解析
     */
    public record SessionView(String id, String name, String buildId, String status, String startedAt,
                              String directory, String reportUrl, List<AgentAvailability> agents) {
    }

    /**
     * 注册表中的被测服务配置。
     */
    public record AgentTarget(CoverageProperties.Agent agent) {

        public String describe() {
            return agent.getName() + "@" + agent.getHost() + ":" + agent.getPort();
        }
    }

    /**
     * 运行时注册一个服务的请求体。
     *
     * @param name               服务名，需与被测服务的 jacocoagent sessionid 一致
     * @param host               Agent 所在主机，默认 127.0.0.1
     * @param agentPort          jacocoagent tcpserver 端口
     * @param classesDirectories 报告分析用的编译产物目录，相对路径以工作区根目录为基准
     * @param sourceDirectories  源码目录，用于生成行级覆盖明细
     * @param replace            服务已存在或来自配置文件时是否覆盖
     */
    public record ServiceRegistrationRequest(String name, String host, int agentPort,
                                             List<String> classesDirectories, List<String> sourceDirectories,
                                             boolean replace) {
    }

    /**
     * 注册表中的一个服务视图。
     *
     * @param runtimeRegistered 是否来自运行时注册（false 表示来自配置文件）
     * @param warnings          目录不存在等需要调用方关注的问题
     */
    public record ServiceView(String name, String host, int agentPort,
                              List<String> classesDirectories, List<String> sourceDirectories,
                              boolean runtimeRegistered, List<String> warnings) {
    }
}
