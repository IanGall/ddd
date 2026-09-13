package cn.iantech.domain.rbac.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class RbacPermissionCodeTest {

    @Test
    void shouldExposeUniqueAndResolvablePermissionCatalog() {
        long uniqueCodes = Arrays.stream(RbacPermissionCode.values())
                .map(RbacPermissionCode::getCode)
                .distinct()
                .count();

        assertEquals(RbacPermissionCode.values().length, uniqueCodes);
        Arrays.stream(RbacPermissionCode.values()).forEach(permission -> {
            assertSame(permission, RbacPermissionCode.require(permission.getCode()));
            assertFalse(permission.getResource().isBlank());
            assertFalse(permission.getAction().isBlank());
            assertFalse(permission.getDescription().isBlank());
            assertNotNull(permission.getRiskLevel());
        });
    }

    @Test
    void shouldRejectUnregisteredPermissionCode() {
        assertThrows(IllegalArgumentException.class, () -> RbacPermissionCode.require("rbac:unknown"));
    }
}
