package cn.iantech.coverage.controller.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 路径解析工具：把配置中的相对路径统一解析为绝对路径。
 *
 * <p>以「工作区根目录」为基准，即同时包含 {@code ddd-base} 与 Gateway 工程的目录。
 * 定位顺序：</p>
 * <ol>
 *     <li>从 {@code user.dir} 向上查找包含全部同级工程目录的父目录；</li>
 *     <li>退回查找包含 {@code .git} 的目录；</li>
 *     <li>都找不到时使用当前工作目录。</li>
 * </ol>
 *
 * <p>这样无论从 {@code ddd-base} 根目录还是从 controller 模块目录启动控制器，
 * 配置里写的 {@code ian-ddd-gateway/...} 这类工作区相对路径都能命中。</p>
 */
public final class PathResolver {

    /**
     * 工作区根目录下应当存在的同级工程目录，用于识别根目录位置。
     */
    private static final List<String> WORKSPACE_MARKERS = List.of("ddd-base", "ian-ddd-gateway");

    private static final String GIT_MARKER = ".git";

    private PathResolver() {
    }

    public static Path workspaceRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path candidate = current;
        while (candidate != null) {
            if (isWorkspaceRoot(candidate)) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        candidate = current;
        while (candidate != null) {
            if (Files.exists(candidate.resolve(GIT_MARKER))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        return current;
    }

    /**
     * 解析单个路径：绝对路径原样返回，相对路径基于工作区根目录解析。
     */
    public static Path resolve(String value) {
        Path path = Path.of(value);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return workspaceRoot().resolve(path).normalize();
    }

    private static boolean isWorkspaceRoot(Path directory) {
        return WORKSPACE_MARKERS.stream().allMatch(marker -> Files.isDirectory(directory.resolve(marker)));
    }
}
