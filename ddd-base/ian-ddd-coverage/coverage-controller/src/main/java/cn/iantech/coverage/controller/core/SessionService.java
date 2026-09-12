package cn.iantech.coverage.controller.core;

import cn.iantech.coverage.controller.config.CoverageProperties;
import cn.iantech.coverage.controller.report.ReportOutcome;
import cn.iantech.coverage.controller.report.ReportService;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.tools.ExecFileLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Session 编排：一次测试对应一个 Session，串联 reset → dump → merge → report 流程。
 *
 * <p>同一时刻只允许一个处于 RUNNING 状态的 Session，避免并发测试互相污染覆盖率数据。</p>
 */
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private static final DateTimeFormatter ID_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private static final String STATUS_RUNNING = "RUNNING";

    private static final String STATUS_FINISHED = "FINISHED";

    private static final int CONNECT_TIMEOUT_MILLIS = 1_500;

    private final AgentRegistry registry;

    private final AgentClient agentClient;

    private final ExecMergeService mergeService;

    private final ReportService reportService;

    private final Path workDirectory;

    private final Map<String, SessionState> sessions = new LinkedHashMap<>();

    private String activeSessionId;

    public SessionService(AgentRegistry registry, AgentClient agentClient, ExecMergeService mergeService,
                          ReportService reportService, Path workDirectory) {
        this.registry = registry;
        this.agentClient = agentClient;
        this.mergeService = mergeService;
        this.reportService = reportService;
        this.workDirectory = workDirectory.toAbsolutePath().normalize();
    }

    /**
     * 创建 Session，并检查各 Agent 的连通性。
     */
    public synchronized CoverageModels.SessionView start(String name, String buildId) {
        if (activeSessionId != null) {
            throw new IllegalStateException("已存在处于 RUNNING 状态的 Session: " + activeSessionId
                    + "，请先 finish 后再创建新的 Session");
        }
        String id = ID_FORMAT.format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0, 5);
        Path directory = workDirectory.resolve("sessions").resolve(id);
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new CoverageException("创建 Session 目录失败: " + directory + " -> " + e.getMessage(), e);
        }
        SessionState state = new SessionState(id, name, buildId, Instant.now(), directory);
        sessions.put(id, state);
        activeSessionId = id;
        List<CoverageModels.AgentAvailability> availability = inspectAgents();
        if (availability.stream().noneMatch(CoverageModels.AgentAvailability::reachable)) {
            log.warn("Session {} 已创建，但没有任何 Agent 可达，请检查被测服务是否以 jacocoagent tcpserver 模式启动", id);
        }
        log.info("Session {} 创建完成，目录: {}", id, directory);
        return toView(state, availability);
    }

    /**
     * 清零所有（或指定）Agent 的探针计数。
     */
    public synchronized CoverageModels.DumpReport reset(String sessionId, List<String> agentNames) {
        SessionState state = requireRunning(sessionId);
        List<CoverageModels.AgentDumpResult> results = new ArrayList<>();
        for (CoverageProperties.Agent agent : registry.resolve(agentNames)) {
            CoverageModels.AgentTarget target = new CoverageModels.AgentTarget(agent);
            try {
                agentClient.reset(target);
                results.add(new CoverageModels.AgentDumpResult(agent.getName(), true, null, "reset ok"));
            } catch (RuntimeException e) {
                log.warn("reset Agent 失败: {} -> {}", target.describe(), e.getMessage());
                results.add(new CoverageModels.AgentDumpResult(agent.getName(), false, null, e.getMessage()));
            }
        }
        log.info("Session {} reset: 成功 {} 个，失败 {} 个", sessionId,
                results.stream().filter(CoverageModels.AgentDumpResult::success).count(),
                results.stream().filter(result -> !result.success()).count());
        return toDumpReport(state, results);
    }

    /**
     * 采集所有（或指定）Agent 当前累积的 execution data，并把结果累积写入各服务的 exec 文件。
     * 单个 Agent 失败不会中断流程，失败信息会随响应返回。
     */
    public synchronized CoverageModels.DumpReport dump(String sessionId, List<String> agentNames) {
        SessionState state = requireRunning(sessionId);
        List<CoverageModels.AgentDumpResult> results = new ArrayList<>();
        for (CoverageProperties.Agent agent : registry.resolve(agentNames)) {
            CoverageModels.AgentTarget target = new CoverageModels.AgentTarget(agent);
            Path execFile = state.directory().resolve(agent.getName() + ".exec");
            try {
                appendDump(target, execFile);
                results.add(new CoverageModels.AgentDumpResult(agent.getName(), true, execFile.toString(), "dump ok"));
            } catch (RuntimeException e) {
                log.warn("dump Agent 失败: {} -> {}", target.describe(), e.getMessage());
                results.add(new CoverageModels.AgentDumpResult(agent.getName(), false, null, e.getMessage()));
            }
        }
        log.info("Session {} dump: 成功 {} 个，失败 {} 个", sessionId,
                results.stream().filter(CoverageModels.AgentDumpResult::success).count(),
                results.stream().filter(result -> !result.success()).count());
        return toDumpReport(state, results);
    }

    /**
     * 把 Agent 本次 dump 的探头数据与已有 exec 合并后写回，保证多次 dump 可以累积。
     */
    private void appendDump(CoverageModels.AgentTarget target, Path execFile) {
        ExecutionDataStore incoming = new ExecutionDataStore();
        agentClient.dump(target, incoming);
        try {
            ExecFileLoader loader = new ExecFileLoader();
            if (Files.isRegularFile(execFile)) {
                loader.load(execFile.toFile());
            }
            for (ExecutionData data : incoming.getContents()) {
                loader.getExecutionDataStore().put(data);
            }
            loader.save(execFile.toFile(), false);
        } catch (IOException e) {
            throw new CoverageException("写入 exec 失败: " + execFile + " -> " + e.getMessage(), e);
        }
    }

    /**
     * 结束 Session：合并 exec、生成报告，并让各 Agent 断开与控制器监听端口的连接。
     *
     * <p>无论报告是否成功生成，Session 都会退出 RUNNING 状态，避免采集失败后锁死后续测试。</p>
     */
    public synchronized ReportOutcome finish(String sessionId) {
        SessionState state = requireRunning(sessionId);
        try {
            ReportOutcome outcome = doFinish(state);
            log.info("Session {} 已结束，整体行覆盖率: {}%，报告: {}", sessionId,
                    outcome.overall().lineRatio(), outcome.dashboardFile());
            if (outcome.hasConsistencyIssue()) {
                log.warn("Session {} 存在 classId 失配，以下类的覆盖率不可信：\n{}",
                        sessionId, outcome.describeConsistency());
            }
            return outcome;
        } finally {
            activeSessionId = null;
        }
    }

    private ReportOutcome doFinish(SessionState state) {
        // 取一次注册表快照：保证合并、分析与断开连接阶段看到的是同一组服务
        AgentRegistry.Snapshot snapshot = registry.snapshot();
        List<Path> execFiles = new ArrayList<>();
        for (CoverageProperties.Agent agent : snapshot.agentList()) {
            Path execFile = state.directory().resolve(agent.getName() + ".exec");
            if (Files.isRegularFile(execFile)) {
                execFiles.add(execFile);
            }
        }
        if (execFiles.isEmpty()) {
            throw new CoverageException("Session " + state.id()
                    + " 没有采集到任何 execution data，无法生成报告；请确认测试期间执行过 dump，且被测服务已注入 jacocoagent");
        }
        Path merged = state.directory().resolve("overall.exec");
        mergeService.merge(execFiles, merged);
        ReportOutcome outcome = reportService.generate(state.directory(), merged, state.id());
        for (CoverageProperties.Agent agent : snapshot.agentList()) {
            try {
                agentClient.disconnect(new CoverageModels.AgentTarget(agent));
            } catch (RuntimeException e) {
                log.debug("断开 Agent 连接失败（可忽略）: {} -> {}", agent.getName(), e.getMessage());
            }
        }
        state.finish(outcome);
        return outcome;
    }

    /**
     * 查询 Session 当前状态。
     */
    public synchronized CoverageModels.SessionView describe(String sessionId) {
        return toView(requireSession(sessionId), inspectAgents());
    }

    /**
     * 查询 Session 报告产物，供控制器直接对外提供静态报告。
     */
    public synchronized Optional<ReportOutcome> findReport(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId)).map(SessionState::outcome);
    }

    /**
     * 合并若干已完成 Session 的 execution data，生成一份并集报告。
     *
     * <p>一次测试运行会按测试类产生多个 Session，每个 Session 只覆盖自己触达的代码路径。
     * 单个 Session 的数字无法代表整轮测试的覆盖率，因此需要把它们的 exec 合并后再出报告。</p>
     *
     * @param name       合并会话名称，用于报告标识；为空时使用 merged
     * @param sessionIds 参与合并的 Session，必须都已存在且完成
     */
    public synchronized ReportOutcome merge(String name, List<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            throw new CoverageException("合并报告至少需要一个 Session");
        }
        List<Path> execFiles = new ArrayList<>();
        for (String sessionId : sessionIds) {
            SessionState state = requireSession(sessionId);
            if (state.outcome() == null) {
                throw new CoverageException("Session " + sessionId + " 尚未 finish，无法参与合并");
            }
            addExecFile(execFiles, state.directory().resolve("overall.exec"));
            for (CoverageProperties.Agent agent : registry.allAgents()) {
                addExecFile(execFiles, state.directory().resolve(agent.getName() + ".exec"));
            }
        }
        if (execFiles.isEmpty()) {
            throw new CoverageException("待合并的 Session 中没有任何 *.exec 文件: " + sessionIds);
        }
        String mergedName = name == null || name.isBlank() ? "merged" : name;
        String id = ID_FORMAT.format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0, 5);
        Path directory = workDirectory.resolve("sessions").resolve(id);
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new CoverageException("创建合并报告目录失败: " + directory + " -> " + e.getMessage(), e);
        }
        Path merged = directory.resolve("overall.exec");
        mergeService.merge(execFiles, merged);
        ReportOutcome outcome = reportService.generate(directory, merged, id);
        SessionState mergedState = new SessionState(id, mergedName, null, Instant.now(), directory);
        mergedState.finish(outcome);
        sessions.put(id, mergedState);
        log.info("合并 {} 个 Session 完成，并集行覆盖率: {}%，报告: {}", sessionIds.size(),
                outcome.overall().lineRatio(), outcome.dashboardFile());
        return outcome;
    }

    private void addExecFile(List<Path> execFiles, Path execFile) {
        if (Files.isRegularFile(execFile)) {
            execFiles.add(execFile);
        }
    }

    public Path workDirectory() {
        return workDirectory;
    }

    /**
     * 控制器关闭时清理仍然处于 RUNNING 的 Session。
     */
    public synchronized void shutdown() {
        if (activeSessionId != null) {
            log.warn("控制器关闭，Session {} 仍处于 RUNNING 状态", activeSessionId);
            activeSessionId = null;
        }
    }

    private List<CoverageModels.AgentAvailability> inspectAgents() {
        List<CoverageModels.AgentAvailability> availability = new ArrayList<>();
        for (CoverageProperties.Agent agent : registry.allAgents()) {
            boolean reachable = isReachable(agent);
            availability.add(new CoverageModels.AgentAvailability(agent.getName(), reachable,
                    reachable ? "tcpserver 可连接" : "连接失败，请确认服务已启动且注入了 jacocoagent"));
        }
        return List.copyOf(availability);
    }

    private boolean isReachable(CoverageProperties.Agent agent) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(agent.getHost(), agent.getPort()), CONNECT_TIMEOUT_MILLIS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private SessionState requireRunning(String sessionId) {
        SessionState state = requireSession(sessionId);
        if (!state.id().equals(activeSessionId)) {
            throw new IllegalStateException("Session " + sessionId + " 不处于 RUNNING 状态");
        }
        return state;
    }

    private SessionState requireSession(String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            throw new IllegalArgumentException("不存在的 Session: " + sessionId);
        }
        return state;
    }

    private CoverageModels.SessionView toView(SessionState state, List<CoverageModels.AgentAvailability> availability) {
        String reportUrl = state.outcome() == null
                ? "/api/coverage/sessions/" + state.id() + "/reports/index.html"
                : state.outcome().overallReportUrl();
        return new CoverageModels.SessionView(state.id(), state.name(), state.buildId(),
                state.outcome() == null ? STATUS_RUNNING : STATUS_FINISHED,
                state.startedAt().toString(), state.directory().toUri().toString(), reportUrl, availability);
    }

    private CoverageModels.DumpReport toDumpReport(SessionState state,
                                                   List<CoverageModels.AgentDumpResult> results) {
        int success = (int) results.stream().filter(CoverageModels.AgentDumpResult::success).count();
        return new CoverageModels.DumpReport(state.id(), success, results.size() - success, List.copyOf(results));
    }

    /**
     * 单个 Session 的内存状态。
     */
    private static final class SessionState {

        private final String id;

        private final String name;

        private final String buildId;

        private final Instant startedAt;

        private final Path directory;

        private ReportOutcome outcome;

        private SessionState(String id, String name, String buildId, Instant startedAt, Path directory) {
            this.id = id;
            this.name = name;
            this.buildId = buildId;
            this.startedAt = startedAt;
            this.directory = directory;
        }

        private void finish(ReportOutcome value) {
            this.outcome = value;
        }

        private String id() {
            return id;
        }

        private String name() {
            return name;
        }

        private String buildId() {
            return buildId;
        }

        private Instant startedAt() {
            return startedAt;
        }

        private Path directory() {
            return directory;
        }

        private ReportOutcome outcome() {
            return outcome;
        }
    }
}
