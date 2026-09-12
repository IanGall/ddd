package cn.iantech.gateway.config;

import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 渠道 HMAC 协议解析与 Canonical Request 构造器。
 */
public final class ChannelCanonicalRequest {

    public static final String CHANNEL_CODE_HEADER = "X-Channel-Code";
    public static final String SECRET_VERSION_HEADER = "X-Channel-Secret-Version";
    public static final String TIMESTAMP_HEADER = "X-Channel-Timestamp";
    public static final String CONTENT_SHA256_HEADER = "X-Channel-Content-SHA256";
    public static final String SIGNATURE_HEADER = "X-Channel-Signature";
    public static final int MAX_BODY_BYTES = 1024 * 1024;
    /** 渠道请求体大小上限（MiB），与 {@link #MAX_BODY_BYTES} 保持一致，用于错误提示。 */
    public static final int MAX_BODY_MIB = MAX_BODY_BYTES / (1024 * 1024);

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern CHANNEL_CODE = Pattern.compile("ch_[A-Za-z0-9_-]{22}");
    private static final HexFormat HEX = HexFormat.of();

    private ChannelCanonicalRequest() {
    }

    public static Material create(HttpServletRequest request, byte[] body) {
        rejectUnsupportedEncoding(request);
        String channelCode = requiredSingleHeader(request, CHANNEL_CODE_HEADER);
        String secretVersionText = requiredSingleHeader(request, SECRET_VERSION_HEADER);
        String timestampText = requiredSingleHeader(request, TIMESTAMP_HEADER);
        String claimedBodyHash = requiredSingleHeader(request, CONTENT_SHA256_HEADER);
        String signature = requiredSingleHeader(request, SIGNATURE_HEADER);

        if (!CHANNEL_CODE.matcher(channelCode).matches()
                || !SHA256_HEX.matcher(claimedBodyHash).matches()
                || !SHA256_HEX.matcher(signature).matches()) {
            throw authenticationFailed();
        }

        long secretVersion = positiveLong(secretVersionText);
        long timestamp = positiveLong(timestampText);
        String actualBodyHash = sha256Hex(body);
        if (!MessageDigest.isEqual(actualBodyHash.getBytes(StandardCharsets.US_ASCII),
                claimedBodyHash.getBytes(StandardCharsets.US_ASCII))) {
            throw authenticationFailed();
        }

        String canonical = String.join("\n",
                request.getMethod().toUpperCase(Locale.ROOT),
                canonicalPath(request.getRequestURI()),
                canonicalQuery(request.getQueryString()),
                canonicalContentType(request.getContentType(), body.length),
                channelCode,
                Long.toString(secretVersion),
                Long.toString(timestamp),
                actualBodyHash);
        return new Material(channelCode, secretVersion, timestamp, signature, canonical);
    }

    private static String requiredSingleHeader(HttpServletRequest request, String name) {
        Enumeration<String> values = request.getHeaders(name);
        if (values == null || !values.hasMoreElements()) {
            throw authenticationFailed();
        }
        String value = values.nextElement();
        if (values.hasMoreElements() || value == null || value.isBlank() || value.length() > 512) {
            throw authenticationFailed();
        }
        return value.trim();
    }

    private static long positiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw authenticationFailed();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw authenticationFailed();
        }
    }

    private static void rejectUnsupportedEncoding(HttpServletRequest request) {
        String contentEncoding = request.getHeader("Content-Encoding");
        if (contentEncoding != null && !contentEncoding.isBlank()
                && !"identity".equalsIgnoreCase(contentEncoding.trim())) {
            throw invalidRequest("渠道请求不支持压缩请求体");
        }
    }

    static String canonicalContentType(String contentType, int bodyLength) {
        if (contentType == null || contentType.isBlank()) {
            if (bodyLength == 0) {
                return "";
            }
            throw invalidRequest("非空请求体必须声明 application/json; charset=UTF-8");
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            if (!MediaType.APPLICATION_JSON.includes(mediaType)
                    || mediaType.getParameters().keySet().stream().anyMatch(key -> !"charset".equalsIgnoreCase(key))
                    || (mediaType.getCharset() != null && !StandardCharsets.UTF_8.equals(mediaType.getCharset()))) {
                throw invalidRequest("渠道请求仅支持 application/json; charset=UTF-8");
            }
            return bodyLength == 0 ? "" : MediaType.APPLICATION_JSON_VALUE;
        } catch (IllegalArgumentException exception) {
            throw invalidRequest("Content-Type 不合法");
        }
    }

    static String canonicalPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank() || rawPath.charAt(0) != '/' || rawPath.indexOf('\\') >= 0) {
            throw invalidRequest("请求路径不合法");
        }
        validatePercentEncoding(rawPath);
        String normalized = uppercasePercentEncoding(rawPath);
        for (String segment : normalized.split("/", -1)) {
            String decoded = decodePercent(segment);
            if (".".equals(decoded) || "..".equals(decoded) || decoded.indexOf('/') >= 0 || decoded.indexOf('\\') >= 0) {
                throw invalidRequest("请求路径包含非法片段");
            }
        }
        return normalized;
    }

    static String canonicalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return "";
        }
        if (rawQuery.length() > 8192) {
            throw invalidRequest("查询参数过长");
        }
        List<QueryPart> parts = new ArrayList<>();
        for (String pair : rawQuery.split("&", -1)) {
            int separator = pair.indexOf('=');
            String rawKey = separator < 0 ? pair : pair.substring(0, separator);
            String rawValue = separator < 0 ? "" : pair.substring(separator + 1);
            String key = percentEncode(decodePercent(rawKey));
            String value = percentEncode(decodePercent(rawValue));
            parts.add(new QueryPart(key, value));
        }
        parts.sort(Comparator.comparing(QueryPart::key).thenComparing(QueryPart::value));
        return parts.stream().map(part -> part.key() + "=" + part.value()).collect(Collectors.joining("&"));
    }

    private static String decodePercent(String value) {
        validatePercentEncoding(value);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int index = 0; index < value.length(); ) {
            char current = value.charAt(index);
            if (current == '%') {
                output.write(Integer.parseInt(value.substring(index + 1, index + 3), 16));
                index += 3;
                continue;
            }
            int codePoint = value.codePointAt(index);
            output.writeBytes(new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8));
            index += Character.charCount(codePoint);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(output.toByteArray())).toString();
        } catch (CharacterCodingException exception) {
            throw invalidRequest("请求包含非法 UTF-8 编码");
        }
    }

    private static String percentEncode(String value) {
        StringBuilder encoded = new StringBuilder();
        for (byte current : value.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = current & 0xff;
            if (isUnreserved(unsigned)) {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%').append(Character.toUpperCase(Character.forDigit(unsigned >>> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(unsigned & 0xf, 16)));
            }
        }
        return encoded.toString();
    }

    private static boolean isUnreserved(int value) {
        return value >= 'a' && value <= 'z' || value >= 'A' && value <= 'Z'
                || value >= '0' && value <= '9' || value == '-' || value == '.' || value == '_' || value == '~';
    }

    private static void validatePercentEncoding(String value) {
        for (int index = value.indexOf('%'); index >= 0; index = value.indexOf('%', index + 3)) {
            if (index + 2 >= value.length() || Character.digit(value.charAt(index + 1), 16) < 0
                    || Character.digit(value.charAt(index + 2), 16) < 0) {
                throw invalidRequest("请求包含非法百分号编码");
            }
        }
    }

    private static String uppercasePercentEncoding(String value) {
        StringBuilder normalized = new StringBuilder(value);
        for (int index = value.indexOf('%'); index >= 0; index = value.indexOf('%', index + 3)) {
            normalized.setCharAt(index + 1, Character.toUpperCase(value.charAt(index + 1)));
            normalized.setCharAt(index + 2, Character.toUpperCase(value.charAt(index + 2)));
        }
        return normalized.toString();
    }

    private static String sha256Hex(byte[] value) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 算法", exception);
        }
    }

    private static AppException authenticationFailed() {
        return new AppException(Constants.ResponseCode.AUTH_REQUIRED.getCode(), "渠道认证失败");
    }

    private static AppException invalidRequest(String message) {
        return new AppException(Constants.ResponseCode.INVALID_ARGUMENT.getCode(), message);
    }

    public record Material(String channelCode, long secretVersion, long timestamp, String signature,
                           String canonicalRequest) {
    }

    private record QueryPart(String key, String value) {
    }
}
