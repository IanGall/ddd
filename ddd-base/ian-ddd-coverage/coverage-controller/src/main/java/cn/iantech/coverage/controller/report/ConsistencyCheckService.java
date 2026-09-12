package cn.iantech.coverage.controller.report;

import cn.iantech.coverage.controller.config.CoverageProperties;
import cn.iantech.coverage.controller.config.PathResolver;
import cn.iantech.coverage.controller.core.AgentRegistry;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.tools.ExecFileLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * classId 一致性校验。
 *
 * <p>比对每个 Agent 上报的 execution data 与报告目录分析出的字节码：若同一个类两边的 classId 不同，
 * 说明被测进程加载的字节码与报告使用的 {@code target/classes} 不是同一次构建产物，
 * 该类在报告里会被误报为 0% 覆盖。</p>
 */
public class ConsistencyCheckService {

    private static final Logger log = LoggerFactory.getLogger(ConsistencyCheckService.class);

    private static final int MAX_REPORTED_MISMATCHES = 10;

    private final AgentRegistry registry;

    public ConsistencyCheckService(AgentRegistry registry) {
        this.registry = registry;
    }

    /**
     * 逐 Agent 校验，返回「Agent 名称 → 校验结果」。
     *
     * @param sessionDirectory 会话产物目录，各 Agent 的 exec 文件位于其中
     */
    public Map<String, ConsistencyReport> check(Path sessionDirectory) {
        AgentRegistry.Snapshot snapshot = registry.snapshot();
        return check(sessionDirectory, snapshot);
    }

    /**
     * 用指定注册表快照校验，保证与报告生成使用同一组服务定义。
     */
    public Map<String, ConsistencyReport> check(Path sessionDirectory, AgentRegistry.Snapshot snapshot) {
        Map<String, Long> localClassIds = localClassIds(snapshot.reportList());
        Map<String, ConsistencyReport> reports = new LinkedHashMap<>();
        for (CoverageProperties.Agent agent : snapshot.agentList()) {
            Path execFile = sessionDirectory.resolve(agent.getName() + ".exec");
            if (!Files.isRegularFile(execFile)) {
                continue;
            }
            reports.put(agent.getName(), checkAgent(agent.getName(), execFile, localClassIds));
        }
        logSummary(reports);
        return reports;
    }

    private ConsistencyReport checkAgent(String agentName, Path execFile, Map<String, Long> localClassIds) {
        ExecFileLoader loader = new ExecFileLoader();
        try {
            loader.load(execFile.toFile());
        } catch (IOException e) {
            log.warn("Agent [{}] 的 exec 无法读取，跳过 classId 校验: {}", agentName, e.getMessage());
            return new ConsistencyReport(0, 0, 0, List.of());
        }
        int aligned = 0;
        int mismatched = 0;
        int absent = 0;
        List<ConsistencyReport.ClassMismatch> mismatches = new ArrayList<>();
        for (ExecutionData data : loader.getExecutionDataStore().getContents()) {
            Long localId = localClassIds.get(data.getName());
            if (localId == null) {
                // 第三方依赖或未纳入报告范围的类，无法校验
                absent++;
                continue;
            }
            if (localId == data.getId()) {
                aligned++;
                continue;
            }
            mismatched++;
            if (mismatches.size() < MAX_REPORTED_MISMATCHES) {
                mismatches.add(new ConsistencyReport.ClassMismatch(data.getName(),
                        format(data.getId()), format(localId)));
            }
        }
        return new ConsistencyReport(aligned, mismatched, absent, List.copyOf(mismatches));
    }

    /**
     * 分析所有报告目录，得到「类名 → classId」映射，作为比对的基准。
     */
    private Map<String, Long> localClassIds(List<CoverageProperties.ServiceReport> services) {
        Map<String, Long> classIds = new LinkedHashMap<>();
        CoverageBuilder builder = new CoverageBuilder();
        Analyzer analyzer = new Analyzer(new ExecutionDataStore(), builder);
        for (CoverageProperties.ServiceReport service : services) {
            for (String configured : service.getClassesDirectories()) {
                Path directory = PathResolver.resolve(configured);
                if (!Files.isDirectory(directory)) {
                    continue;
                }
                try {
                    analyzer.analyzeAll(directory.toFile());
                } catch (IOException e) {
                    log.warn("分析 {} 失败，该目录不参与 classId 校验: {}", directory, e.getMessage());
                }
            }
        }
        for (IClassCoverage coverage : builder.getClasses()) {
            classIds.putIfAbsent(coverage.getName(), coverage.getId());
        }
        return classIds;
    }

    private void logSummary(Map<String, ConsistencyReport> reports) {
        reports.forEach((agent, report) -> {
            if (report.hasMismatch()) {
                log.warn("Agent [{}] {} ", agent, report.describe());
            } else {
                log.info("Agent [{}] {}", agent, report.describe());
            }
        });
    }

    private String format(long classId) {
        return "0x" + Long.toHexString(classId);
    }
}
