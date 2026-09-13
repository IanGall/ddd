package cn.iantech.trigger.convertor;

import cn.iantech.api.model.customer.CustomerUserDTO;
import cn.iantech.domain.customer.model.CustomerUserEntity;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C 端用户领域实体到 RPC DTO 的字段映射契约，敏感字段（密码哈希）不得外泄。
 */
class CustomerApiConverterTest {

    @Test
    void shouldMapPublicFields() {
        CustomerUserEntity entity = CustomerUserEntity.builder()
                .id(7L)
                .loginName("13800000000")
                .passwordHash("$2a$10$should-not-leak")
                .displayName("测试用户")
                .avatar("https://cdn.example/a.png")
                .status(Boolean.TRUE)
                .deleted(Boolean.FALSE)
                .build();

        CustomerUserDTO dto = CustomerApiConverter.toDTO(entity);

        assertEquals(7L, dto.getId());
        assertEquals("13800000000", dto.getLoginName());
        assertEquals("测试用户", dto.getDisplayName());
        assertEquals("https://cdn.example/a.png", dto.getAvatar());
        assertEquals(Boolean.TRUE, dto.getStatus());
    }

    @Test
    void shouldNotExposeCredentialFields() {
        boolean leaked = Arrays.stream(CustomerUserDTO.class.getDeclaredFields())
                .anyMatch(field -> field.getName().toLowerCase().contains("password")
                        || field.getName().toLowerCase().contains("secret"));

        assertTrue(!leaked, "CustomerUserDTO 不得包含密码/密钥材料字段");
    }

    @Test
    void shouldKeepNullOptionalsAsNull() {
        CustomerUserDTO dto = CustomerApiConverter.toDTO(CustomerUserEntity.builder().id(8L).build());

        assertEquals(8L, dto.getId());
        assertNull(dto.getLoginName());
        assertNull(dto.getAvatar());
    }
}
