package cn.iantech.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AppExceptionTest {

    @Test
    void shouldExposeCodeAsMessageWhenOnlyCodeIsProvided() {
        AppException exception = new AppException("E001");

        assertEquals("E001", exception.getCode());
        assertEquals("E001", exception.getMessage());
        assertNull(exception.getInfo());
        assertEquals(AppException.class.getName() + ": E001", exception.toString());
    }

    @Test
    void shouldExposeBusinessMessageAndCause() {
        IllegalArgumentException cause = new IllegalArgumentException("参数错误");

        AppException exception = new AppException("E002", "业务处理失败", cause);

        assertEquals("E002", exception.getCode());
        assertEquals("业务处理失败", exception.getInfo());
        assertEquals("业务处理失败", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

}
