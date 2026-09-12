package cn.iantech.coverage.controller.report;

import java.util.List;

/**
 * 覆盖率一致性问题排查结果。
 *
 * <p>JaCoCo 按 classId 匹配 execution data 与字节码。当被测进程加载的字节码与生成报告所用的
 * {@code target/classes} 不是同一次构建产物时，classId 会不一致：JaCoCo 不会报错，
 * 而是把该类当作「未覆盖」，覆盖率静默变成 0%。</p>
 */
public record ConsistencyReport(int alignedCount, int mismatchedCount, int absentCount,
                                List<ClassMismatch> mismatches) {

    /**
     * 是否存在会导致覆盖率失真的失配。
     */
    public boolean hasMismatch() {
        return mismatchedCount > 0;
    }

    /**
     * 面向测试日志的提示文本，失配时给出可操作的排查方向。
     */
    public String describe() {
        if (!hasMismatch()) {
            return "classId 校验通过：已对齐 " + alignedCount + " 个类";
        }
        StringBuilder text = new StringBuilder("classId 校验发现 ")
                .append(mismatchedCount)
                .append(" 个类的字节码与报告目录不一致，这些类的覆盖率会被误报为 0%：\n");
        for (ClassMismatch mismatch : mismatches) {
            text.append("    ").append(mismatch.className())
                    .append("（被测进程 ").append(mismatch.execClassId())
                    .append(" / 报告目录 ").append(mismatch.localClassId()).append("）\n");
        }
        text.append("  原因：被测服务启动后源码被重新编译，或服务与报告使用了不同次构建的产物。\n")
                .append("  处理：clean 重新构建后重启被测服务，再执行测试。");
        return text.toString();
    }

    /**
     * 单个类的 classId 失配记录。
     *
     * @param className    类名（斜杠分隔）
     * @param execClassId  被测进程上报的 classId
     * @param localClassId 报告目录分析出的 classId
     */
    public record ClassMismatch(String className, String execClassId, String localClassId) {
    }
}
