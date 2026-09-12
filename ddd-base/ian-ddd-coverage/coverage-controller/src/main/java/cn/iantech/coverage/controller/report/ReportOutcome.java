package cn.iantech.coverage.controller.report;

import java.util.List;
import java.util.Map;

/**
 * 报告生成结果。
 *
 * @param consistency 各 Agent 的 classId 一致性校验结果（Agent 名称 → 校验详情），
 *                    存在失配时说明对应类的覆盖率被误报为 0%
 */
public record ReportOutcome(String dashboardFile, String summaryFile, String overallReportUrl,
                            List<CoverageSummary> services, CoverageSummary overall,
                            Map<String, ConsistencyReport> consistency) {

    /**
     * 是否存在会导致覆盖率失真的 classId 失配。
     */
    public boolean hasConsistencyIssue() {
        return consistency != null && consistency.values().stream().anyMatch(ConsistencyReport::hasMismatch);
    }

    /**
     * 校验结果的汇总文本，供测试日志与接口调用方展示。
     */
    public String describeConsistency() {
        if (consistency == null || consistency.isEmpty()) {
            return "classId 校验：未采集到 execution data";
        }
        StringBuilder text = new StringBuilder();
        consistency.forEach((agent, report) -> text.append("  ").append(agent).append(": ")
                .append(report.describe()).append("\n"));
        return text.toString().stripTrailing();
    }
}
