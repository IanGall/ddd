package cn.iantech.common.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageResponseTest {

    @Test
    void shouldBuildPageResponseWithItems() {
        PageResponse<String> response = PageResponse.<String>builder()
                .total(2L).pageNum(1).pageSize(10).list(List.of("first", "second")).build();

        assertEquals(2L, response.getTotal());
        assertEquals(1, response.getPageNum());
        assertEquals(10, response.getPageSize());
        assertEquals(List.of("first", "second"), response.getList());
    }

    @Test
    void shouldReturnEmptyListWhenNoItems() {
        PageResponse<String> response = PageResponse.<String>builder().total(0L).list(List.of()).build();

        assertEquals(0L, response.getTotal());
        assertTrue(response.getList().isEmpty());
    }

    @Test
    void shouldFollowEqualsAndHashCodeContract() {
        PageResponse<String> left = PageResponse.<String>builder().total(1L).list(List.of("item")).build();
        PageResponse<String> right = PageResponse.<String>builder().total(1L).list(List.of("item")).build();

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
    }
}
