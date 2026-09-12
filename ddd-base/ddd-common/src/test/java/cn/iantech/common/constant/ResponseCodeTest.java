package cn.iantech.common.constant;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseCodeTest {

    @Test
    void shouldUseUniqueSemanticResponseCodes() {
        assertTrue(Arrays.stream(Constants.ResponseCode.values())
                .allMatch(responseCode -> responseCode.name().equals(responseCode.getCode())));
        assertEquals(Constants.ResponseCode.values().length,
                Arrays.stream(Constants.ResponseCode.values())
                        .map(Constants.ResponseCode::getCode)
                        .distinct()
                        .count());
    }

    // 验证资源不存在和状态冲突使用稳定的通用 HTTP 语义
    @Test
    void shouldExposeResourceFailureResponseCodes() {
        assertEquals(404, Constants.ResponseCode.NOT_FOUND.getHttpStatus());
        assertEquals(409, Constants.ResponseCode.CONFLICT.getHttpStatus());
        assertEquals(Constants.ResponseCode.NOT_FOUND, Constants.ResponseCode.fromCode("NOT_FOUND"));
        assertEquals(Constants.ResponseCode.CONFLICT, Constants.ResponseCode.fromCode("CONFLICT"));
    }
}
