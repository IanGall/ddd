package cn.iantech.coverage.junit;

import cn.iantech.coverage.junit.CoverageClient.CoverageClientException;
import cn.iantech.coverage.junit.CoverageClient.DumpResponse;
import cn.iantech.coverage.junit.CoverageClient.ReportResponse;
import cn.iantech.coverage.junit.CoverageClient.SessionResponse;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 分布式覆盖率 JUnit 5 扩展。
 *
 * <p>生命周期与覆盖率采集的对应关系：</p>
 * <pre>
 * beforeAll  → start session + reset agents
 * afterEach  → dump agents（无论成功失败都会执行）
 * afterAll   → finish：merge + 报告
 * </pre>
 *
 * <p>控制器不可用、Agent 不可达等异常默认只记录日志、不改变测试结果，
 * 可通过 {@code coverage.fail-on-error=true} 调整。</p>
 */
public class CoversE2eExtension implements BeforeAllCallback, AfterEachCallback, AfterAllCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(CoversE2eExtension.class);

    private static final String SESSION_KEY = "coverage-session";

    @Override
    public void beforeAll(ExtensionContext context) {
        CoverageExtensionConfig config = CoverageExtensionConfig.load();
        CoversE2e annotation = context.getRequiredTestClass().getAnnotation(CoversE2e.class);
        if (!config.enabled()) {
            log(context, "覆盖率采集已通过 coverage.enabled=false 关闭");
            return;
        }
        if (annotation == null) {
            return;
        }
        CoverageClient client = new CoverageClient(config.controllerUrl());
        if (!client.isAvailable()) {
            String message = "覆盖率控制器不可用（" + config.controllerUrl() + "），跳过本次覆盖率采集";
            if (config.failOnError()) {
                throw new CoverageClientException(message);
            }
            log(context, message);
            return;
        }
        List<String> agentNames = resolveAgents(annotation, config);
        String sessionName = annotation.value().isBlank()
                ? context.getRequiredTestClass().getName()
                : annotation.value();
        try {
            SessionResponse session = client.startSession(sessionName,
                    annotation.buildId().isBlank() ? null : annotation.buildId());
            context.getStore(NAMESPACE).put(SESSION_KEY, session.id());
            log(context, "Session " + session.id() + " 已创建，采集 " + CoverageClient.describe(agentNames));
            List<String> unreachable = session.unreachableAgents();
            if (!unreachable.isEmpty()) {
                String message = "以下 Agent 不可达: " + unreachable
                        + "，请确认对应服务已注入 jacocoagent 并处于运行状态";
                if (config.requireReachableAgents() && config.failOnError()) {
                    throw new CoverageClientException(message);
                }
                log(context, message);
            }
            DumpResponse reset = client.reset(session.id(), agentNames);
            log(context, "reset 完成: 成功 " + reset.successCount() + " 个，失败 " + reset.failedCount() + " 个");
        } catch (CoverageClientException e) {
            handle(context, config, "创建覆盖率 Session 失败: " + e.getMessage());
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        Optional<String> sessionId = sessionId(context);
        if (sessionId.isEmpty()) {
            return;
        }
        CoverageExtensionConfig config = CoverageExtensionConfig.load();
        CoverageClient client = new CoverageClient(config.controllerUrl());
        CoversE2e annotation = context.getRequiredTestClass().getAnnotation(CoversE2e.class);
        try {
            DumpResponse dump = client.dump(sessionId.get(), resolveAgents(annotation, config));
            log(context, "dump 完成: 成功 " + dump.successCount() + " 个，失败 " + dump.failedCount() + " 个");
            List<String> failed = dump.failedAgents();
            if (!failed.isEmpty()) {
                log(context, "以下 Agent dump 失败，覆盖率可能不完整: " + failed);
            }
        } catch (CoverageClientException e) {
            // dump 失败不能覆盖测试本身的失败原因，仅记录
            log(context, "dump 覆盖率失败: " + e.getMessage());
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        Optional<String> sessionId = sessionId(context);
        if (sessionId.isEmpty()) {
            return;
        }
        CoverageExtensionConfig config = CoverageExtensionConfig.load();
        CoverageClient client = new CoverageClient(config.controllerUrl());
        try {
            ReportResponse report = client.finish(sessionId.get());
            StringBuilder summary = new StringBuilder("\n[Coverage] 本次测试覆盖率汇总（Session ")
                    .append(sessionId.get()).append("）\n")
                    .append("------------------------------------------------------------\n")
                    .append(report.describe()).append("\n")
                    .append("------------------------------------------------------------\n")
                    .append("报告入口: ").append(config.controllerUrl()).append(report.overallReportUrl())
                    .append("\n仪表盘: ").append(report.dashboardFile())
                    .append("\nXML: ").append(report.summaryFile());
            String consistencyIssue = report.describeConsistencyIssue();
            if (!consistencyIssue.isEmpty()) {
                summary.append("\n------------------------------------------------------------\n")
                        .append("[Coverage] 警告：classId 校验未通过\n")
                        .append(consistencyIssue)
                        .append("\n------------------------------------------------------------");
            }
            System.out.println(summary);
        } catch (CoverageClientException e) {
            handle(context, config, "生成覆盖率报告失败: " + e.getMessage());
        }
    }

    private List<String> resolveAgents(CoversE2e annotation, CoverageExtensionConfig config) {
        if (annotation != null && annotation.agents().length > 0) {
            return List.of(annotation.agents());
        }
        return config.agentNames();
    }

    /**
     * 从方法级或类级 ExtensionContext 中取出当前测试类对应的 Session。
     */
    private Optional<String> sessionId(ExtensionContext context) {
        List<ExtensionContext> candidates = new ArrayList<>();
        candidates.add(context);
        context.getParent().ifPresent(candidates::add);
        for (ExtensionContext candidate : candidates) {
            String value = candidate.getStore(NAMESPACE).get(SESSION_KEY, String.class);
            if (value != null) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private void handle(ExtensionContext context, CoverageExtensionConfig config, String message) {
        if (config.failOnError()) {
            throw new CoverageClientException(message);
        }
        log(context, message);
    }

    private void log(ExtensionContext context, String message) {
        System.out.println("[Coverage] " + context.getRequiredTestClass().getSimpleName() + " - " + message);
    }
}
