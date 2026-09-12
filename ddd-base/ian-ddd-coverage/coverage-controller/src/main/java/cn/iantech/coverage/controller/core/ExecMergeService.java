package cn.iantech.coverage.controller.core;

import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.ExecutionDataWriter;
import org.jacoco.core.data.SessionInfo;
import org.jacoco.core.tools.ExecFileLoader;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 把各服务产出的 {@code *.exec} 合并成一个 overall.exec。
 */
public class ExecMergeService {

    /**
     * 合并 exec 文件，并写入一条汇总 Session 信息，便于在报告中区分采集轮次。
     *
     * @param execFiles 各服务的 exec 文件，允许为空
     * @param target    合并结果输出位置
     */
    public void merge(List<Path> execFiles, Path target) {
        try {
            ExecutionDataStore dataStore = new ExecutionDataStore();
            for (Path execFile : execFiles) {
                if (!Files.isRegularFile(execFile)) {
                    continue;
                }
                ExecFileLoader loader = new ExecFileLoader();
                loader.load(execFile.toFile());
                for (ExecutionData data : loader.getExecutionDataStore().getContents()) {
                    dataStore.put(data);
                }
            }
            Files.createDirectories(target.getParent());
            write(dataStore, target);
        } catch (IOException e) {
            throw new CoverageException("合并 exec 失败: " + target + " -> " + e.getMessage(), e);
        }
    }

    private void write(ExecutionDataStore dataStore, Path target) throws IOException {
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(target.toFile()))) {
            ExecutionDataWriter writer = new ExecutionDataWriter(output);
            SessionInfo sessionInfo = new SessionInfo("coverage-controller", System.currentTimeMillis(),
                    System.currentTimeMillis());
            writer.visitSessionInfo(sessionInfo);
            for (ExecutionData data : dataStore.getContents()) {
                writer.visitClassExecution(data);
            }
        }
    }
}
