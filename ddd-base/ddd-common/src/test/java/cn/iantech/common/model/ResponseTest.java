package cn.iantech.common.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResponseTest {

    @Test
    void shouldBuildResponseWithData() {
        Response<String> response = Response.<String>builder()
                .code("SUCCESS").info("成功").data("payload").build();

        assertEquals("SUCCESS", response.getCode());
        assertEquals("成功", response.getInfo());
        assertEquals("payload", response.getData());
    }

    @Test
    void shouldAllowNullableInfoAndData() {
        Response<Void> response = Response.<Void>builder().code("SUCCESS").build();

        assertNull(response.getInfo());
        assertNull(response.getData());
    }

    @Test
    void shouldSupportNoArgsConstructorAndSetters() {
        Response<String> response = new Response<>();
        response.setCode("SUCCESS");
        response.setInfo("成功");
        response.setData("payload");

        assertEquals("SUCCESS", response.getCode());
        assertEquals("成功", response.getInfo());
        assertEquals("payload", response.getData());
    }

    @Test
    void shouldFollowEqualsAndHashCodeContract() {
        Response<String> left = Response.<String>builder().code("SUCCESS").info("成功").build();
        Response<String> right = Response.<String>builder().code("SUCCESS").info("成功").build();
        Response<String> other = Response.<String>builder().code("INTERNAL_ERROR").build();

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertNotEquals(left, other);
    }
}
