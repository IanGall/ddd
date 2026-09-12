package cn.iantech.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Constants {

    /**
     * 认证令牌类型（tokenKind），服务端签发与网关校验共用，取值进入 RPC 契约。
     */
    public static final class TokenKind {

        public static final String OPAQUE = "OPAQUE";
        public static final String CHANNEL_HMAC = "CHANNEL_HMAC";

        private TokenKind() {
        }
    }

    /**
     * 授权范围（authorizedScope）常量，取值进入 RPC 契约。
     */
    public static final class AuthScope {

        public static final String EXTERNAL_ACCESS = "external:access";

        private AuthScope() {
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public enum ResponseCode {

        SUCCESS("SUCCESS", "成功", 200),
        INTERNAL_ERROR("INTERNAL_ERROR", "系统内部错误", 500),
        INVALID_ARGUMENT("INVALID_ARGUMENT", "请求参数不合法", 400),
        PAYLOAD_TOO_LARGE("PAYLOAD_TOO_LARGE", "请求体超过上限", 413),
        AUTH_REQUIRED("AUTH_REQUIRED", "需要认证", 401),
        AUTH_UNAVAILABLE("AUTH_UNAVAILABLE", "认证服务暂不可用", 503),
        AUTH_REFRESH_BUSY("AUTH_REFRESH_BUSY", "刷新请求处理中，请稍后重试", 409),
        AUTH_RATE_LIMITED("AUTH_RATE_LIMITED", "登录尝试过于频繁，请稍后重试", 429),
        ACCESS_DENIED("ACCESS_DENIED", "无权访问", 403),
        RPC_ERROR("RPC_ERROR", "下游服务调用失败", 502),
        RPC_NO_PROVIDER("RPC_NO_PROVIDER", "下游服务暂无可用提供者", 503),
        RPC_TIMEOUT("RPC_TIMEOUT", "下游服务调用超时", 504),
        NOT_FOUND("NOT_FOUND", "资源不存在", 404),
        CONFLICT("CONFLICT", "资源状态冲突", 409),
        ;

        private static final Map<String, ResponseCode> BY_CODE = Arrays.stream(values())
                .collect(Collectors.toUnmodifiableMap(ResponseCode::getCode, Function.identity()));

        private String code;
        private String info;
        private int httpStatus;

        public static ResponseCode fromCode(String code) {
            return code == null ? null : BY_CODE.get(code);
        }

    }

}
