package cn.iantech.coverage.controller.report;

import org.jacoco.core.analysis.IBundleCoverage;

/**
 * 报告汇总数据，用于在控制台/仪表盘上展示整体与服务级覆盖率。
 */
public record CoverageSummary(String name, int classCount, int lineTotal, int lineCovered, int lineMissed,
                              int branchTotal, int branchCovered) {

    public static CoverageSummary of(String name, IBundleCoverage bundle) {
        return new CoverageSummary(name,
                bundle.getClassCounter().getTotalCount(),
                bundle.getLineCounter().getTotalCount(),
                bundle.getLineCounter().getCoveredCount(),
                bundle.getLineCounter().getMissedCount(),
                bundle.getBranchCounter().getTotalCount(),
                bundle.getBranchCounter().getCoveredCount());
    }

    private static double ratio(int covered, int total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.round(covered * 10000.0 / total) / 100.0;
    }

    /**
     * 行覆盖率，0~100，四舍五入保留两位小数。
     */
    public double lineRatio() {
        return ratio(lineCovered, lineTotal);
    }

    /**
     * 分支覆盖率，0~100，四舍五入保留两位小数。
     */
    public double branchRatio() {
        return ratio(branchCovered, branchTotal);
    }
}
