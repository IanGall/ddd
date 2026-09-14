package cn.iantech.gateway.core;

import cn.iantech.common.constant.Constants;
import cn.iantech.gateway.core.config.ChannelCanonicalRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 渠道 HMAC 章节的文档契约：README 里写给对接方的**线协议字面量**必须与代码常量一致。
 *
 * <p>渠道方是照 README 实现签名的，这些字符串与数值就是接口本身：重命名常量、调整 Body 上限或改授权范围
 * 却漏改文档，会让对接方按过期协议实现，而单侧编译与测试都不会报错。故把两侧绑进构建。</p>
 *
 * <p>被比对的值统一收敛在 {@code cn.iantech.common.constant.Constants}：
 * 请求头名与 Body 上限在 {@link ChannelCanonicalRequest}，授权范围在 {@code Constants.AuthScope}，
 * 时钟偏移窗口与防重放 TTL 在 {@code Constants.ChannelAuth}（原为认证服务的私有常量，
 * 已上移共享，避免「值在认证服务、文档在网关」的错位）。</p>
 */
class GatewayChannelHmacDocContractTest {

    private static final String SECTION_HEADER = "### 渠道 HMAC 请求";

    /** 五个渠道请求头，顺序与协议一致。 */
    private static final List<String> REQUIRED_HEADERS = List.of(
            ChannelCanonicalRequest.CHANNEL_CODE_HEADER,
            ChannelCanonicalRequest.SECRET_VERSION_HEADER,
            ChannelCanonicalRequest.TIMESTAMP_HEADER,
            ChannelCanonicalRequest.CONTENT_SHA256_HEADER,
            ChannelCanonicalRequest.SIGNATURE_HEADER);

    @Test
    void documentedChannelHeadersMustMatchProtocolConstants() throws IOException {
        List<String> headerBlock = headerSampleBlock();

        List<String> missing = REQUIRED_HEADERS.stream()
                .filter(header -> headerBlock.stream().noneMatch(line -> line.startsWith(header + ":")))
                .toList();

        Assertions.assertTrue(missing.isEmpty(),
                () -> "README 渠道 HMAC 示例缺少与代码常量一致的请求头（文件：%s）：%s"
                        .formatted(gatewayReadme(), missing));
    }

    @Test
    void documentedScopeAndBodyLimitMustMatchConstants() throws IOException {
        String section = hmacSection();

        Assertions.assertTrue(section.contains(Constants.AuthScope.EXTERNAL_ACCESS),
                () -> "README 渠道 HMAC 章节未写明固定授权范围 " + Constants.AuthScope.EXTERNAL_ACCESS);

        String documentedLimit = ChannelCanonicalRequest.MAX_BODY_MIB + " MiB";
        Assertions.assertTrue(section.contains(documentedLimit),
                () -> "README 渠道 HMAC 章节未写明原始 Body 上限 " + documentedLimit
                        + "（应与 ChannelCanonicalRequest.MAX_BODY_MIB 一致）");
    }

    @Test
    void documentedTimeWindowAndReplayTtlMustMatchConstants() throws IOException {
        String section = normalizedHmacSection();

        String clockSkew = Constants.ChannelAuth.CLOCK_SKEW_SECONDS + " 秒";
        String replayTtl = Constants.ChannelAuth.REPLAY_TTL_SECONDS + " 秒";

        Assertions.assertTrue(section.contains(clockSkew),
                () -> "README 渠道 HMAC 章节未写明允许的时钟偏移 " + clockSkew
                        + "（应与 Constants.ChannelAuth.CLOCK_SKEW_SECONDS 一致）");
        Assertions.assertTrue(section.contains(replayTtl),
                () -> "README 渠道 HMAC 章节未写明防重放登记时长 " + replayTtl
                        + "（应与 Constants.ChannelAuth.REPLAY_TTL_SECONDS 一致）");
    }

    /** 取渠道 HMAC 章节内 ```http 代码块的内容行。 */
    private List<String> headerSampleBlock() throws IOException {
        List<String> section = hmacSectionLines();
        int fenceStart = -1;
        for (int index = 0; index < section.size(); index++) {
            if (section.get(index).trim().equalsIgnoreCase("```http")) {
                fenceStart = index;
                break;
            }
        }
        Assertions.assertTrue(fenceStart >= 0, () -> "README 渠道 HMAC 章节找不到 ```http 请求头示例");

        return section.subList(fenceStart + 1, section.size()).stream()
                .takeWhile(line -> !line.trim().startsWith("```"))
                .map(String::trim)
                .toList();
    }

    private String hmacSection() throws IOException {
        return String.join("\n", hmacSectionLines());
    }

    /** README 按列宽硬换行（如「登记 600\n秒」），比较数值前先归一化空白。 */
    private String normalizedHmacSection() throws IOException {
        return hmacSection().replaceAll("\\s+", " ");
    }

    /** 从章节标题开始，取到下一个同级标题为止。 */
    private List<String> hmacSectionLines() throws IOException {
        List<String> lines = Files.readAllLines(gatewayReadme(), StandardCharsets.UTF_8);
        int start = lines.indexOf(SECTION_HEADER);
        Assertions.assertTrue(start >= 0, () -> "README 中找不到章节 " + SECTION_HEADER);

        return lines.subList(start + 1, lines.size()).stream()
                .takeWhile(line -> !line.startsWith("### "))
                .toList();
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
