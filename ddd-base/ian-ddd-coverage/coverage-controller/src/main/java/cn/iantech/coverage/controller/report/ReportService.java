package cn.iantech.coverage.controller.report;

import cn.iantech.coverage.controller.config.CoverageProperties;
import cn.iantech.coverage.controller.config.PathResolver;
import cn.iantech.coverage.controller.core.AgentRegistry;
import cn.iantech.coverage.controller.core.CoverageException;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.*;
import org.jacoco.report.html.HTMLFormatter;
import org.jacoco.report.xml.XMLFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于合并后的 execution data 生成各服务报告与整体报告。
 *
 * <p>目录约定（sessionDir 为一次测试会话的产物目录）：</p>
 * <pre>
 * sessionDir/
 *   overall.exec           合并后的 execution data
 *   reports/
 *     index.html           整体报告
 *     overall.xml          整体 XML 报告（供 CI 解析）
 *     gateway/index.html   各服务报告
 *     order/index.html
 *   dashboard.html         覆盖率总览
 * </pre>
 */
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private static final String TITLE = "Distributed Coverage";

    private static final int SOURCE_TAB_WIDTH = 4;

    private final AgentRegistry registry;

    private final ConsistencyCheckService consistencyCheckService;

    public ReportService(AgentRegistry registry, ConsistencyCheckService consistencyCheckService) {
        this.registry = registry;
        this.consistencyCheckService = consistencyCheckService;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * 生成各服务报告 + 整体报告 + 总览仪表盘。
     *
     * @param sessionDir 会话产物目录
     * @param mergedExec 合并后的 exec 文件
     * @param sessionId  会话标识，写入报告页脚
     */
    public ReportOutcome generate(Path sessionDir, Path mergedExec, String sessionId) {
        // 取一次注册表快照，保证加载 exec、分析类、生成报告看到的是同一组服务
        AgentRegistry.Snapshot snapshot = registry.snapshot();
        List<CoverageProperties.ServiceReport> services = snapshot.reportList();
        ExecFileLoader loader = loadExecutionData(sessionDir, mergedExec, services);

        List<CoverageSummary> serviceSummaries = new ArrayList<>();
        List<Path> allClasses = new ArrayList<>();
        ISourceFileLocator allSources = sourceLocator(allSourceDirectories(services));

        for (CoverageProperties.ServiceReport service : services) {
            List<Path> classesDirectories = existingDirectories(service.getClassesDirectories());
            if (classesDirectories.isEmpty()) {
                log.warn("服务 [{}] 的 classes 目录不存在，已跳过该服务报告: {}",
                        service.getName(), service.getClassesDirectories());
                continue;
            }
            IBundleCoverage bundle = analyze(loader, classesDirectories);
            serviceSummaries.add(CoverageSummary.of(service.getName(), bundle));
            allClasses.addAll(classesDirectories);
            writeServiceReport(sessionDir, service.getName(), bundle,
                    sourceLocator(existingDirectories(service.getSourceDirectories())), loader);
        }

        if (allClasses.isEmpty()) {
            throw new CoverageException("没有任何可分析的服务 classes 目录，请检查 coverage.reports 配置");
        }

        IBundleCoverage overall = analyze(loader, allClasses);
        CoverageSummary overallSummary = CoverageSummary.of("overall", overall);
        Path reportsDirectory = sessionDir.resolve("reports");
        writeOverallReport(reportsDirectory, mergedExec, loader, overall, allSources);
        Path dashboardFile = sessionDir.resolve("dashboard.html");
        writeDashboard(sessionDir, dashboardFile, sessionId, serviceSummaries, overallSummary);
        Map<String, ConsistencyReport> consistency = consistencyCheckService.check(sessionDir, snapshot);

        return new ReportOutcome(sessionId, dashboardFile.toString(),
                reportsDirectory.resolve("overall.xml").toString(),
                "/api/coverage/sessions/" + sessionId + "/reports/index.html",
                List.copyOf(serviceSummaries),
                overallSummary,
                consistency);
    }

    /**
     * 加载合并结果以及各服务的 exec，既拿到 execution data 也拿到真实的 Session 信息。
     */
    private ExecFileLoader loadExecutionData(Path sessionDir, Path mergedExec,
                                             List<CoverageProperties.ServiceReport> services) {
        ExecFileLoader loader = new ExecFileLoader();
        load(loader, mergedExec);
        for (CoverageProperties.ServiceReport service : services) {
            load(loader, sessionDir.resolve(service.getName() + ".exec"));
        }
        return loader;
    }

    private void load(ExecFileLoader loader, Path execFile) {
        if (!Files.isRegularFile(execFile)) {
            return;
        }
        try {
            loader.load(execFile.toFile());
        } catch (IOException e) {
            throw new CoverageException("读取 " + execFile.getFileName() + " 失败 -> " + e.getMessage(), e);
        }
    }

    private void writeServiceReport(Path sessionDir, String serviceName, IBundleCoverage bundle,
                                    ISourceFileLocator sources, ExecFileLoader loader) {
        Path target = sessionDir.resolve("reports").resolve(serviceName);
        HTMLFormatter formatter = htmlFormatter(serviceName);
        try {
            writeReport(formatter.createVisitor(new FileMultiReportOutput(target.toFile())), bundle, sources, loader);
        } catch (IOException e) {
            throw new CoverageException("生成服务 [" + serviceName + "] 报告失败 -> " + e.getMessage(), e);
        }
    }

    private void writeOverallReport(Path reportsDirectory, Path mergedExec, ExecFileLoader loader,
                                    IBundleCoverage bundle, ISourceFileLocator sources) {
        HTMLFormatter htmlFormatter = htmlFormatter(TITLE + " - overall");
        try {
            writeReport(htmlFormatter.createVisitor(new FileMultiReportOutput(reportsDirectory.toFile())),
                    bundle, sources, loader);
        } catch (IOException e) {
            throw new CoverageException("生成整体 HTML 报告失败 -> " + e.getMessage(), e);
        }
        XMLFormatter xmlFormatter = new XMLFormatter();
        xmlFormatter.setOutputEncoding(StandardCharsets.UTF_8.name());
        try (var output = Files.newOutputStream(reportsDirectory.resolve("overall.xml"))) {
            writeReport(xmlFormatter.createVisitor(output), bundle, sources, loader);
        } catch (IOException e) {
            throw new CoverageException("生成 overall.xml 失败 -> " + e.getMessage(), e);
        }
        log.info("合并后的 execution data: {}", mergedExec);
    }

    /**
     * 按 JaCoCo 报告协议输出：先写 Session 信息，再写 bundle，最后收尾生成索引页。
     */
    private void writeReport(IReportVisitor visitor, IBundleCoverage bundle, ISourceFileLocator sources,
                             ExecFileLoader loader) throws IOException {
        visitor.visitInfo(loader.getSessionInfoStore().getInfos(),
                loader.getExecutionDataStore().getContents());
        visitor.visitBundle(bundle, sources);
        visitor.visitEnd();
    }

    private HTMLFormatter htmlFormatter(String footerText) {
        HTMLFormatter formatter = new HTMLFormatter();
        formatter.setOutputEncoding(StandardCharsets.UTF_8.name());
        formatter.setFooterText(footerText);
        return formatter;
    }

    private IBundleCoverage analyze(ExecFileLoader loader, List<Path> classesDirectories) {
        CoverageBuilder coverage = new CoverageBuilder();
        Analyzer analyzer = new Analyzer(loader.getExecutionDataStore(), coverage);
        try {
            for (Path directory : classesDirectories) {
                analyzer.analyzeAll(directory.toFile());
            }
        } catch (IOException e) {
            throw new CoverageException("分析字节码失败 -> " + e.getMessage(), e);
        }
        return coverage.getBundle(TITLE);
    }

    private ISourceFileLocator sourceLocator(List<Path> sourceDirectories) {
        MultiSourceFileLocator locator = new MultiSourceFileLocator(SOURCE_TAB_WIDTH);
        for (Path directory : sourceDirectories) {
            locator.add(new DirectorySourceFileLocator(directory.toFile(), StandardCharsets.UTF_8.name(),
                    SOURCE_TAB_WIDTH));
        }
        return locator;
    }

    private List<Path> existingDirectories(List<String> configured) {
        List<Path> directories = new ArrayList<>();
        for (String value : configured) {
            Path path = PathResolver.resolve(value);
            if (Files.isDirectory(path)) {
                directories.add(path);
            }
        }
        return directories;
    }

    private List<Path> allSourceDirectories(List<CoverageProperties.ServiceReport> services) {
        List<Path> directories = new ArrayList<>();
        for (CoverageProperties.ServiceReport service : services) {
            directories.addAll(existingDirectories(service.getSourceDirectories()));
        }
        return directories;
    }

    private void writeDashboard(Path sessionDir, Path dashboardFile, String sessionId,
                                List<CoverageSummary> services, CoverageSummary overall) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n")
                .append("<meta charset=\"UTF-8\">\n")
                .append("<title>").append(escape(TITLE)).append("</title>\n")
                .append("<style>\n")
                .append("body{font-family:-apple-system,'Segoe UI',Roboto,'PingFang SC',sans-serif;")
                .append("margin:40px;color:#1f2933;background:#f7f9fc;}\n")
                .append("h1{font-size:24px;}\n")
                .append("table{border-collapse:collapse;background:#fff;min-width:640px;")
                .append("box-shadow:0 1px 3px rgba(0,0,0,.08);}\n")
                .append("th,td{padding:10px 16px;border-bottom:1px solid #e4e7eb;text-align:left;}\n")
                .append("th{background:#f0f4f8;font-weight:600;}\n")
                .append("td.num{text-align:right;font-variant-numeric:tabular-nums;}\n")
                .append("a{color:#2563eb;text-decoration:none;}\n")
                .append("a:hover{text-decoration:underline;}\n")
                .append("tr.overall{font-weight:700;background:#eef4ff;}\n")
                .append(".meta{color:#52606d;font-size:13px;}\n")
                .append("</style>\n</head>\n<body>\n")
                .append("<h1>").append(escape(TITLE)).append("</h1>\n")
                .append("<p class=\"meta\">session: ").append(escape(sessionId)).append("</p>\n")
                .append("<table>\n<tr><th>Service</th><th>Classes</th><th>Line</th>")
                .append("<th>Line&nbsp;%</th><th>Branch</th><th>Branch&nbsp;%</th></tr>\n");
        for (CoverageSummary service : services) {
            appendRow(html, service, "reports/" + service.name() + "/index.html", false);
        }
        appendRow(html, overall, "reports/index.html", true);
        html.append("</table>\n</body>\n</html>\n");
        try {
            Files.createDirectories(sessionDir);
            try (Writer writer = Files.newBufferedWriter(dashboardFile, StandardCharsets.UTF_8)) {
                writer.write(html.toString());
            }
        } catch (IOException e) {
            throw new CoverageException("写入 dashboard.html 失败 -> " + e.getMessage(), e);
        }
    }

    private void appendRow(StringBuilder html, CoverageSummary summary, String href, boolean overall) {
        html.append(overall ? "<tr class=\"overall\">" : "<tr>")
                .append("<td><a href=\"").append(escape(href)).append("\">")
                .append(escape(summary.name())).append("</a></td>")
                .append("<td class=\"num\">").append(summary.classCount()).append("</td>")
                .append("<td class=\"num\">").append(summary.lineCovered())
                .append(" / ").append(summary.lineTotal()).append("</td>")
                .append("<td class=\"num\">").append(summary.lineRatio()).append("%</td>")
                .append("<td class=\"num\">").append(summary.branchCovered())
                .append(" / ").append(summary.branchTotal()).append("</td>")
                .append("<td class=\"num\">").append(summary.branchRatio()).append("%</td>")
                .append("</tr>\n");
    }
}
