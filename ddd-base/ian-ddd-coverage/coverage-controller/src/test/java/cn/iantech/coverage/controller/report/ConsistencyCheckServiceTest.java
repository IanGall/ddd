package cn.iantech.coverage.controller.report;

import cn.iantech.coverage.controller.config.CoverageProperties;
import cn.iantech.coverage.controller.core.AgentRegistry;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.ExecutionDataWriter;
import org.jacoco.core.data.SessionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 classId 一致性校验能识别出「被测进程字节码与报告目录不同源」的问题。
 *
 * <p>用测试类自身作为样本：它位于 {@code target/test-classes}，与报告目录指向同一份字节码，
 * 因此可以通过伪造 classId 来模拟不同源的情况。</p>
 */
class ConsistencyCheckServiceTest {

    private static final String SAMPLE_CLASS = ConsistencyCheckServiceTest.class.getName();

    /**
     * JaCoCo 内部类名：斜杠分隔且不带 .class 后缀。
     */
    private static final String SAMPLE_INTERNAL_NAME = SAMPLE_CLASS.replace('.', '/');

    /**
     * 文件系统中的相对路径。
     */
    private static final String SAMPLE_FILE_NAME = SAMPLE_INTERNAL_NAME + ".class";

    @Test
    void shouldDetectClassIdMismatch(@TempDir Path tempDir) throws Exception {
        Path classesDirectory = tempDir.resolve("classes");
        Path sessionDirectory = Files.createDirectories(tempDir.resolve("session"));
        Path classFile = copySampleClass(classesDirectory);
        long localClassId = analyzeClassId(classFile);

        // 故意写入不同的 classId，模拟被测进程与报告目录不同源
        writeExec(sessionDirectory.resolve("fake.exec"), hitting(localClassId + 1));

        ConsistencyReport report = check(classesDirectory, sessionDirectory);

        assertTrue(report.hasMismatch(), "classId 不同必须被识别为失配");
        assertEquals(1, report.mismatchedCount());
        assertEquals(SAMPLE_INTERNAL_NAME, report.mismatches().get(0).className());
        assertTrue(report.describe().contains("clean"), "提示文本应给出重建指引");
    }

    @Test
    void shouldPassWhenClassIdMatches(@TempDir Path tempDir) throws Exception {
        Path classesDirectory = tempDir.resolve("classes");
        Path sessionDirectory = Files.createDirectories(tempDir.resolve("session"));
        Path classFile = copySampleClass(classesDirectory);
        long localClassId = analyzeClassId(classFile);

        Path execFile = sessionDirectory.resolve("fake.exec");
        writeExec(execFile, hitting(localClassId));

        ConsistencyReport report = check(classesDirectory, sessionDirectory);

        assertFalse(report.hasMismatch(), "同源构建不应报告失配");
        assertEquals(1, report.alignedCount());
        assertEquals(0, report.mismatchedCount());
    }

    /**
     * 把当前测试类复制到临时目录，作为「报告目录」的内容。
     */
    private Path copySampleClass(Path classesDirectory) throws Exception {
        Path classesRoot = Path.of(ConsistencyCheckServiceTest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        Path source = classesRoot.resolve(SAMPLE_FILE_NAME);
        assertTrue(Files.isRegularFile(source), "样本类文件应存在: " + source);
        Path target = classesDirectory.resolve(SAMPLE_FILE_NAME);
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private ConsistencyReport check(Path classesDirectory, Path sessionDirectory) throws Exception {
        CoverageProperties properties = new CoverageProperties();
        CoverageProperties.Agent agent = new CoverageProperties.Agent();
        agent.setName("fake");
        properties.setAgents(List.of(agent));
        CoverageProperties.ServiceReport report = new CoverageProperties.ServiceReport();
        report.setName("fake");
        report.setClassesDirectories(List.of(classesDirectory.toString()));
        properties.setReports(List.of(report));

        Map<String, ConsistencyReport> reports =
                new ConsistencyCheckService(new AgentRegistry(properties)).check(sessionDirectory);
        return reports.get("fake");
    }

    /**
     * 用 JaCoCo 分析字节码，取出校验基准使用的 classId。
     *
     * <p>注意 {@code IClassCoverage.getName()} 返回斜杠分隔的内部名，与 execution data 中的类名一致。</p>
     */
    private long analyzeClassId(Path classFile) throws Exception {
        ExecutionDataStore store = new ExecutionDataStore();
        CoverageBuilder builder = new CoverageBuilder();
        new Analyzer(store, builder).analyzeClass(Files.readAllBytes(classFile), SAMPLE_CLASS);
        for (IClassCoverage coverage : builder.getClasses()) {
            if (SAMPLE_CLASS.equals(coverage.getName().replace('/', '.'))) {
                return coverage.getId();
            }
        }
        throw new IllegalStateException("未分析出目标类: " + SAMPLE_CLASS);
    }

    /**
     * 构造带命中的 execution data。
     *
     * <p>{@link ExecutionDataWriter#visitClassExecution} 只输出 {@code hasHits()} 为真的数据，
     * 全 false 的探针会被静默跳过，因此这里必须让至少一个探针命中。</p>
     */
    private ExecutionData hitting(long classId) {
        ExecutionData data = new ExecutionData(classId, SAMPLE_INTERNAL_NAME, 3);
        data.getProbes()[0] = true;
        return data;
    }

    /**
     * 写入 exec 文件。
     */
    private void writeExec(Path target, ExecutionData data) throws Exception {
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(target.toFile()))) {
            ExecutionDataWriter writer = new ExecutionDataWriter(output);
            writer.visitSessionInfo(new SessionInfo("test", System.currentTimeMillis(),
                    System.currentTimeMillis()));
            writer.visitClassExecution(data);
            writer.flush();
        }
    }
}
