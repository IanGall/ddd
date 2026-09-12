package cn.iantech.test.nplusone;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 单次测试的观测会话：累计 SELECT 与远程调用，并在超出预算时给出可读的失败信息。
 *
 * <p>SQL 先做归一化（去空白、数字字面量替换为 {@code ?}）再计数，因此同一语句带不同参数
 * 会被识别为重复查询。</p>
 */
final class ObservationSession {

    private final int maxSelects;
    private final int maxRepeatedSelects;
    private final int maxRemoteCalls;
    private final int maxRepeatedRemoteCalls;
    private final Pattern[] ignoredSqlPatterns;
    private final String[] ignoredRemoteOperations;

    private final AtomicInteger selects = new AtomicInteger();
    private final AtomicInteger remoteCalls = new AtomicInteger();
    private final Map<String, AtomicInteger> sqlCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> remoteCounts = new ConcurrentHashMap<>();

    ObservationSession(DetectNPlusOne annotation) {
        this.maxSelects = annotation.maxSelects();
        this.maxRepeatedSelects = annotation.maxRepeatedSelects();
        this.maxRemoteCalls = annotation.maxRemoteCalls();
        this.maxRepeatedRemoteCalls = annotation.maxRepeatedRemoteCalls();
        this.ignoredSqlPatterns = Arrays.stream(annotation.ignoredSqlPatterns())
                .map(Pattern::compile)
                .toArray(Pattern[]::new);
        this.ignoredRemoteOperations = annotation.ignoredRemoteOperations();
    }

    private static String normalize(String sql) {
        if (sql == null) {
            return "<null>";
        }
        return sql.replaceAll("\\s+", "").replaceAll("\\b\\d+\\b", "?");
    }

    private static String budgetViolation(String subject, String operation, int actual, int expected) {
        return actual > expected ? formatViolation(subject, operation, actual, expected) : null;
    }

    private static String formatViolation(String subject, String operation, int actual, int expected) {
        return subject + "超过阈值，actual=" + actual + ", expected=" + expected + ", operation=" + operation;
    }

    void recordSelect(String sql) {
        String normalized = normalize(sql);
        if (Arrays.stream(ignoredSqlPatterns).anyMatch(pattern -> pattern.matcher(normalized).matches())) {
            return;
        }
        selects.incrementAndGet();
        sqlCounts.computeIfAbsent(normalized, key -> new AtomicInteger()).incrementAndGet();
    }

    void recordRemote(String operation) {
        if (Arrays.stream(ignoredRemoteOperations).anyMatch(ignored -> ignored.equals(operation))) {
            return;
        }
        remoteCalls.incrementAndGet();
        remoteCounts.computeIfAbsent(operation, key -> new AtomicInteger()).incrementAndGet();
    }

    /**
     * 校验本次观测是否超出预算，存在违规时抛出携带完整违规清单的 {@link AssertionError}。
     */
    void assertWithinLimits() {
        Stream<String> budgetViolations = Stream.of(
                        budgetViolation("SELECT 总数", "全部 SELECT", selects.get(), maxSelects),
                        budgetViolation("远程调用总数", "全部远程调用", remoteCalls.get(), maxRemoteCalls))
                .filter(Objects::nonNull);
        Stream<String> repeatedSqlViolations = sqlCounts.entrySet().stream()
                .filter(entry -> entry.getValue().get() > maxRepeatedSelects)
                .map(entry -> formatViolation("重复 SELECT", entry.getKey(), entry.getValue().get(), maxRepeatedSelects));
        Stream<String> repeatedRemoteViolations = remoteCounts.entrySet().stream()
                .filter(entry -> entry.getValue().get() > maxRepeatedRemoteCalls)
                .map(entry -> formatViolation("重复远程调用", entry.getKey(), entry.getValue().get(),
                        maxRepeatedRemoteCalls));

        String violations = Stream.of(budgetViolations, repeatedSqlViolations, repeatedRemoteViolations)
                .flatMap(stream -> stream)
                .collect(Collectors.joining(System.lineSeparator()));
        if (!violations.isBlank()) {
            throw new AssertionError(System.lineSeparator() + "检测到 N+1：" + System.lineSeparator() + violations);
        }
    }

    /**
     * 观测摘要，写入测试报告，便于校准预算与定位热点语句。
     */
    String summary() {
        String sqlSummary = sqlCounts.entrySet().stream()
                .map(entry -> entry.getKey() + " × " + entry.getValue().get())
                .collect(Collectors.joining("; "));
        String remoteSummary = remoteCounts.entrySet().stream()
                .map(entry -> entry.getKey() + " × " + entry.getValue().get())
                .collect(Collectors.joining("; "));
        return "selects=" + selects.get()
                + ", remoteCalls=" + remoteCalls.get()
                + ", sql=" + sqlSummary
                + ", remote=" + remoteSummary;
    }
}
