package cn.iantech.coverage.controller;

import java.nio.file.Path;

/**
 * 测试环境工作目录覆盖，供 PathResolver 解析相对路径。
 */
public final class WorkDirectory {

    private static final String PROPERTY = "coverage.test.work-directory";

    private WorkDirectory() {
    }

    public static void override(Path directory) {
        System.setProperty(PROPERTY, directory.toString());
    }

    public static Path current() {
        String value = System.getProperty(PROPERTY);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("测试工作目录未初始化");
        }
        return Path.of(value);
    }

    public static void reset() {
        System.clearProperty(PROPERTY);
    }
}
