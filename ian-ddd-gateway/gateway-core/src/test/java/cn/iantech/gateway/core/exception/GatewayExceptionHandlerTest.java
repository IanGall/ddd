package cn.iantech.gateway.core.exception;

import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.common.model.Response;
import cn.iantech.gateway.core.config.ChannelCanonicalRequest;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewayExceptionHandlerTest {

    private final GatewayExceptionHandler handler = new GatewayExceptionHandler();

    // 未登记的业务码统一按内部错误处理，避免向客户端暴露未知协议。
    @Test
    void shouldMapUnknownCodeToInternalError() {
        ResponseEntity<Response<Void>> response = handler.handleAppException(new AppException("USER_EXISTS", "用户已存在"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(Constants.ResponseCode.INTERNAL_ERROR.getCode(), response.getBody().getCode());
        assertEquals(Constants.ResponseCode.INTERNAL_ERROR.getInfo(), response.getBody().getInfo());
    }

    @Test
    void shouldMapEmptyCodeToInternalError() {
        ResponseEntity<Response<Void>> response = handler.handleAppException(new AppException("", "业务异常"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(Constants.ResponseCode.INTERNAL_ERROR.getCode(), response.getBody().getCode());
    }

    @Test
    void shouldMapNullCodeToInternalError() {
        ResponseEntity<Response<Void>> response = handler.handleAppException(new AppException(null, "业务异常"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(Constants.ResponseCode.INTERNAL_ERROR.getCode(), response.getBody().getCode());
    }

    // 验证授权拒绝映射为 403 响应
    @Test
    void shouldMapAccessDeniedToForbidden() {
        ResponseEntity<Response<Void>> response = handler.handleAppException(
                new AppException(Constants.ResponseCode.ACCESS_DENIED.getCode(), "无权访问"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(Constants.ResponseCode.ACCESS_DENIED.getCode(), response.getBody().getCode());
    }

    @Test
    void shouldMapAuthUnavailableToServiceUnavailable() {
        ResponseEntity<Response<Void>> response = handler.handleAppException(
                new AppException(Constants.ResponseCode.AUTH_UNAVAILABLE.getCode(),
                        Constants.ResponseCode.AUTH_UNAVAILABLE.getInfo()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(Constants.ResponseCode.AUTH_UNAVAILABLE.getCode(), response.getBody().getCode());
    }

    @Test
    void shouldMapAuthRateLimitedToTooManyRequests() {
        ResponseEntity<Response<Void>> response = handler.handleAppException(
                new AppException(Constants.ResponseCode.AUTH_RATE_LIMITED.getCode()));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals(Constants.ResponseCode.AUTH_RATE_LIMITED.getCode(), response.getBody().getCode());
    }

    // 验证参数异常映射为 400，并且不透传底层异常文案。
    @Test
    void shouldMapValidationExceptionToBadRequest() {
        ResponseEntity<Response<Void>> response = handler.handleValidationException(
                new ConstraintViolationException("参数错误", Set.of()));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Constants.ResponseCode.INVALID_ARGUMENT.getCode(), response.getBody().getCode());
        assertEquals(Constants.ResponseCode.INVALID_ARGUMENT.getInfo(), response.getBody().getInfo());
    }

    // 缺少请求头属于客户端参数错误，必须返回 400 而不是兜底 500。
    @Test
    void shouldMapMissingRequestHeaderToBadRequest() {
        ResponseEntity<Response<Void>> response = handler.handleValidationException(
                new MissingRequestHeaderException("X-Platform-Token", null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Constants.ResponseCode.INVALID_ARGUMENT.getCode(), response.getBody().getCode());
    }

    @Test
    void shouldMapOversizedChannelBodyToPayloadTooLarge() {
        ResponseEntity<Response<Void>> response = handler.handlePayloadTooLarge();

        assertEquals(HttpStatus.CONTENT_TOO_LARGE, response.getStatusCode());
        assertEquals(Constants.ResponseCode.PAYLOAD_TOO_LARGE.getCode(), response.getBody().getCode());
        assertEquals("渠道请求体不能超过 " + ChannelCanonicalRequest.MAX_BODY_MIB + " MiB",
                response.getBody().getInfo());
    }

}
