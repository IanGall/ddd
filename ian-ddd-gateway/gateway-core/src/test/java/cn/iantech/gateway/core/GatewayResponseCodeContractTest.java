package cn.iantech.gateway.core;

import cn.iantech.common.constant.Constants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 网关响应码表的文档契约：{@code ian-ddd-gateway/README.md} 中的映射表必须与
 * {@link Constants.ResponseCode} 完全一致。
 *
 * <p>{@code ResponseCode} 是语义码的唯一来源，而 README 的映射表由人工维护，两侧极易漂移——
 * 历史上就出现过「文档里列着 422 与 AUTH_REFRESH_BUSY，但枚举里根本没有这两个码」的情况。
 * 本测试把该漂移变成构建失败，并在失败信息里直接列出需要增/删的行。</p>
 */
class GatewayResponseCodeContractTest {

    /** 成功码不参与错误映射表。 */
    private static final Set<String> EXCLUDED_FROM_TABLE = Set.of(Constants.ResponseCode.SUCCESS.getCode());

    /** 表格首行特征：用它界定待解析区段，避免误读 README 中其他表格。 */
    private static final String TABLE_HEADER = "| 响应码";

    /** 形如 `` `CODE` `` 的语义码单元格（一行可含多个，如 `AUTH_UNAVAILABLE`、`RPC_NO_PROVIDER`）。 */
    private static final Pattern CODE_CELL = Pattern.compile("`([A-Z_]+)`");

    /** 形如 `| 409 |` 的状态码单元格。 */
    private static final Pattern STATUS_CELL = Pattern.compile("\\|\\s*(\\d{3})\\s*\\|");

    @Test
    void readmeTableMustMatchResponseCodeEnum() throws IOException {
        Path readme = gatewayReadme();
        Map<String, Integer> documented = parseDocumentedTable(readme);

        Map<String, Integer> declared = Arrays.stream(Constants.ResponseCode.values())
                .filter(responseCode -> !EXCLUDED_FROM_TABLE.contains(responseCode.getCode()))
                .collect(Collectors.toMap(Constants.ResponseCode::getCode,
                        Constants.ResponseCode::getHttpStatus, (left, right) -> left, LinkedHashMap::new));

        Set<String> missingInDocument = difference(declared.keySet(), documented.keySet());
        Set<String> unknownInDocument = difference(documented.keySet(), declared.keySet());

        Assertions.assertTrue(missingInDocument.isEmpty() && unknownInDocument.isEmpty(),
                () -> """
                        README 响应码表与 Constants.ResponseCode 不一致（文件：%s）
                        文档缺少（枚举有、表里没有）：%s
                        文档多余（表里有、枚举没有）：%s
                        请同步 %s 的映射表"""
                        .formatted(readme, missingInDocument, unknownInDocument, readme.getFileName()));

        List<String> mismatchedStatus = declared.entrySet().stream()
                .filter(entry -> !Objects.equals(entry.getValue(), documented.get(entry.getKey())))
                .map(entry -> "%s：文档 %d ≠ 枚举 %d".formatted(entry.getKey(), documented.get(entry.getKey()),
                        entry.getValue()))
                .toList();

        Assertions.assertTrue(mismatchedStatus.isEmpty(),
                () -> "README 响应码表的 HTTP 状态码与枚举不符（文件：%s）：%s".formatted(readme, mismatchedStatus));
    }

    /**
     * 解析 README 里「响应码 → HTTP 状态码」映射表，忽略表头与分隔行。
     *
     * <p>只解析 {@value #TABLE_HEADER} 起始、下一个空行结束的区段，防止误读文档中其他表格的数字。</p>
     */
    private Map<String, Integer> parseDocumentedTable(Path readme) throws IOException {
        Map<String, Integer> documented = new LinkedHashMap<>();
        List<String> section = tableSection(Files.readAllLines(readme, StandardCharsets.UTF_8));

        for (String line : section) {
            Matcher statusMatcher = STATUS_CELL.matcher(line);
            if (!statusMatcher.find()) {
                continue;
            }
            int httpStatus = Integer.parseInt(statusMatcher.group(1));
            Matcher codeMatcher = CODE_CELL.matcher(line);
            while (codeMatcher.find()) {
                Integer previous = documented.put(codeMatcher.group(1), httpStatus);
                Assertions.assertNull(previous,
                        () -> "README 响应码表出现重复条目: " + codeMatcher.group(1));
            }
        }

        Assertions.assertFalse(documented.isEmpty(),
                () -> "未从 %s 解析出任何响应码，请检查「响应码 → HTTP 状态码」表是否被改动或删除"
                        .formatted(readme));
        return documented;
    }

    private List<String> tableSection(List<String> lines) {
        int start = -1;
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).startsWith(TABLE_HEADER)) {
                start = index;
                break;
            }
        }
        Assertions.assertTrue(start >= 0, () -> "README 中找不到以 " + TABLE_HEADER + " 开头的响应码表");

        return lines.subList(start, lines.size()).stream()
                .takeWhile(line -> !line.isBlank())
                .filter(line -> line.startsWith("|"))
                .toList();
    }

    private Set<String> difference(Set<String> left, Set<String> right) {
        return left.stream().filter(value -> !right.contains(value))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private Path gatewayReadme() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        return Stream.iterate(current, Objects::nonNull, Path::getParent)
                .map(path -> path.resolve("ian-ddd-gateway/README.md"))
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("无法定位 ian-ddd-gateway/README.md: " + current));
    }
}
