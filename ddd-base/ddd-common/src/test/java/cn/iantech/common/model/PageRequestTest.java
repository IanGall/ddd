package cn.iantech.common.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PageRequestTest {

    @Test
    void shouldBuildPageRequest() {
        PageRequest request = PageRequest.builder().pageNum(2).pageSize(20).build();

        assertEquals(2, request.getPageNum());
        assertEquals(20, request.getPageSize());
    }

    @Test
    void shouldAllowNullPagingFields() {
        PageRequest request = PageRequest.builder().build();

        assertNull(request.getPageNum());
        assertNull(request.getPageSize());
    }

    @Test
    void shouldFollowEqualsAndHashCodeContract() {
        PageRequest left = PageRequest.builder().pageNum(1).pageSize(10).build();
        PageRequest right = PageRequest.builder().pageNum(1).pageSize(10).build();

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertNotEquals(left, PageRequest.builder().pageNum(2).pageSize(10).build());
    }
}
