package cn.iantech.test.rbac;

import cn.iantech.api.model.rbac.RbacPermissionDTO;
import cn.iantech.api.model.rbac.RbacRoleDTO;
import cn.iantech.api.model.rbac.RbacUserDTO;
import cn.iantech.domain.rbac.model.entity.RbacPermissionEntity;
import cn.iantech.domain.rbac.model.entity.RbacRoleEntity;
import cn.iantech.domain.rbac.model.entity.RbacUserEntity;
import cn.iantech.trigger.convertor.RbacUserEntityMapper;
import io.github.linpeilie.Converter;
import io.github.linpeilie.mapstruct.MapstructAutoConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.LocalDateTime;
import java.util.List;

@SpringJUnitConfig(RbacMapStructPlusTest.MapperTestConfig.class)
class RbacMapStructPlusTest {

    @Autowired
    private Converter converter;

    @Test
    void shouldConvertRbacEntitiesToApiDtos() {
        LocalDateTime now = LocalDateTime.now();
        RbacUserEntity user = RbacUserEntity.builder()
                .id(1L)
                .username("admin")
                .passwordHash("sensitive-password-hash")
                .displayName("管理员")
                .deleted(Boolean.FALSE)
                .createTime(now)
                .updateTime(now)
                .build();
        RbacRoleEntity role = RbacRoleEntity.builder()
                .id(2L)
                .roleCode("ADMIN")
                .roleName("管理员")
                .deleted(Boolean.FALSE)
                .build();
        RbacPermissionEntity permission = RbacPermissionEntity.builder()
                .id(3L)
                .permCode("rbac:user:query")
                .permName("查询用户")
                .deleted(Boolean.FALSE)
                .build();

        RbacUserDTO userDTO = converter.convert(user, RbacUserDTO.class);
        RbacRoleDTO roleDTO = converter.convert(role, RbacRoleDTO.class);
        RbacPermissionDTO permissionDTO = converter.convert(permission, RbacPermissionDTO.class);

        Assertions.assertEquals(user.getId(), userDTO.getId());
        Assertions.assertEquals(user.getUsername(), userDTO.getUsername());
        Assertions.assertEquals(user.getDisplayName(), userDTO.getDisplayName());
        Assertions.assertEquals(user.getCreateTime(), userDTO.getCreateTime());
        Assertions.assertEquals(role.getRoleCode(), roleDTO.getRoleCode());
        Assertions.assertEquals(permission.getPermCode(), permissionDTO.getPermCode());
    }

    @Test
    void shouldConvertRbacEntityListAndKeepSensitiveFieldsOutOfContract() {
        List<RbacUserDTO> userDTOList = converter.convert(List.of(
                RbacUserEntity.builder().id(1L).username("admin").passwordHash("hash").deleted(Boolean.FALSE).build(),
                RbacUserEntity.builder().id(2L).username("operator").passwordHash("hash").deleted(Boolean.TRUE).build()
        ), RbacUserDTO.class);

        Assertions.assertEquals(List.of("admin", "operator"),
                userDTOList.stream().map(RbacUserDTO::getUsername).toList());
        Assertions.assertThrows(NoSuchFieldException.class,
                () -> RbacUserDTO.class.getDeclaredField("passwordHash"));
        Assertions.assertThrows(NoSuchFieldException.class,
                () -> RbacUserDTO.class.getDeclaredField("deleted"));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(MapstructAutoConfiguration.class)
    @ComponentScan(basePackageClasses = RbacUserEntityMapper.class)
    static class MapperTestConfig {
    }
}
