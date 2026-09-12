package cn.iantech.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomainPageTest {

    @Test
    void shouldKeepPagingMetadataAndTypedItems() {
        DomainPage<String> page = new DomainPage<>(2L, 3, 10, List.of("a", "b"));

        assertEquals(2L, page.total());
        assertEquals(3, page.pageNum());
        assertEquals(10, page.pageSize());
        assertEquals(List.of("a", "b"), page.list());
    }
}
