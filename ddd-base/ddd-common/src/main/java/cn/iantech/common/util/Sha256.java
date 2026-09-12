package cn.iantech.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * SHA-256 摘要工具。
 */
public final class Sha256 {

    private static final String ALGORITHM = "SHA-256";
    private static final HexFormat HEX = HexFormat.of();

    private Sha256() {
    }

    /**
     * 返回输入文本 UTF-8 字节的 SHA-256 十六进制摘要。
     */
    public static String hex(String value) {
        return HEX.formatHex(digest(value));
    }

    /**
     * 返回输入文本 UTF-8 字节的 SHA-256 原始摘要。
     */
    public static byte[] digest(String value) {
        String input = Objects.requireNonNull(value, "摘要输入不能为空");
        try {
            return MessageDigest.getInstance(ALGORITHM).digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 " + ALGORITHM, exception);
        }
    }
}
