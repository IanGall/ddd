package cn.iantech.common.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class BasePOTest {

    @Test
    void shouldBuildBasePOWithAuditFields() {
        LocalDateTime createTime = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        BasePO basePO = BasePO.builder()
                .id(1L).deleted(false).createTime(createTime).updateTime(createTime.plusHours(1)).build();

        assertEquals(1L, basePO.getId());
        assertFalse(basePO.getDeleted());
        assertEquals(createTime, basePO.getCreateTime());
        assertEquals(createTime.plusHours(1), basePO.getUpdateTime());
    }

    @Test
    void shouldAllowNullAuditFields() {
        BasePO basePO = BasePO.builder().build();

        assertNull(basePO.getId());
        assertNull(basePO.getDeleted());
        assertNull(basePO.getCreateTime());
        assertNull(basePO.getUpdateTime());
    }

    @Test
    void shouldFollowEqualsAndHashCodeContract() {
        BasePO left = BasePO.builder().id(1L).deleted(false).build();
        BasePO right = BasePO.builder().id(1L).deleted(false).build();

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
    }
}
