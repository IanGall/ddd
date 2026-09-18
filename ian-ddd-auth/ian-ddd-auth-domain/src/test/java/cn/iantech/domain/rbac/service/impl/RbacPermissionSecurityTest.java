package cn.iantech.domain.rbac.service.impl;

import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.domain.rbac.infra.*;
import cn.iantech.domain.rbac.model.RbacPermissionCode;
import cn.iantech.domain.rbac.model.entity.RbacAccountEntity;
import cn.iantech.domain.rbac.model.entity.RbacPermissionEntity;
import cn.iantech.domain.rbac.service.impl.RbacAccountService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RbacPermissionSecurityTest {

    @Mock
    private IRbacUserRepository userRepository;
    @Mock
    private IRbacRoleRepository roleRepository;
    @Mock
    private IRbacPermissionRepository permissionRepository;
    @Mock
    private IRbacRelationRepository relationRepository;
    @Mock
    private IRbacAccountRepository accountRepository;

    private RbacDomainService domainService;

    @BeforeEach
    void setUp() {
        domainService = new RbacDomainService(userRepository, roleRepository, permissionRepository,
                relationRepository, accountRepository);
    }

    @Test
    void shouldRejectReservedPermissionPrefix() {
        AppException exception = Assertions.assertThrows(AppException.class, () -> domainService.createPermission(
                1L, "rbac:custom:write", "非法系统权限", 2, 0L, "", "", true));

        Assertions.assertEquals(Constants.ResponseCode.INVALID_ARGUMENT.getCode(), exception.getCode());
        verify(permissionRepository, never()).save(anyLong(), any());
    }

    @Test
    void shouldRejectSystemPermissionMutation() {
        RbacPermissionEntity systemPermission = permission(10L, 0L, "rbac:user:read", true);
        when(permissionRepository.findById(1L, 10L)).thenReturn(Optional.of(systemPermission));

        AppException updateException = Assertions.assertThrows(AppException.class, () ->
                domainService.updatePermission(1L, 10L, "新名称", null, null, null, null, null));
        AppException deleteException = Assertions.assertThrows(AppException.class, () ->
                domainService.deletePermission(1L, 10L));

        Assertions.assertEquals(Constants.ResponseCode.INVALID_ARGUMENT.getCode(), updateException.getCode());
        Assertions.assertEquals(Constants.ResponseCode.INVALID_ARGUMENT.getCode(), deleteException.getCode());
        verify(permissionRepository, never()).updateById(anyLong(), any());
        verify(permissionRepository, never()).logicDeleteById(anyLong(), anyLong());
    }

    @Test
    void shouldRejectIndirectPermissionCycle() {
        when(permissionRepository.findById(1L, 10L))
                .thenReturn(Optional.of(permission(10L, 0L, "custom:a", false)));
        // 父链校验改为一次加载账号全量权限后在内存中回溯
        when(permissionRepository.findAllByAccountId(1L))
                .thenReturn(List.of(permission(10L, 0L, "custom:a", false),
                        permission(20L, 30L, "custom:b", false),
                        permission(30L, 10L, "custom:c", false)));

        AppException exception = Assertions.assertThrows(AppException.class, () ->
                domainService.updatePermission(1L, 10L, null, null, 20L, null, null, null));

        Assertions.assertEquals(Constants.ResponseCode.INVALID_ARGUMENT.getCode(), exception.getCode());
        verify(permissionRepository, never()).updateById(anyLong(), any());
    }

    @Test
    void shouldMarkInitializedPermissionsAsSystemManaged() {
        RbacAccountService accountService = new RbacAccountService(accountRepository, permissionRepository);
        when(accountRepository.save(any())).thenReturn(RbacAccountEntity.builder().id(1L).username("admin").build());

        // 口令摘要由调用方（cases 层）在事务外编码后传入，本服务不再持有编码器
        accountService.createAccount("admin", "encoded-password", "管理员", "admin@test.com", "13800000000");

        // 内置权限目录改为一次批量写入，断言与目录长度绑定，避免新增权限码时漏改用例
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RbacPermissionEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(permissionRepository, Mockito.times(1)).saveAll(ArgumentMatchers.eq(1L), captor.capture());
        List<RbacPermissionEntity> saved = captor.getValue();
        Assertions.assertEquals(RbacPermissionCode.values().length, saved.size());
        Assertions.assertTrue(saved.stream().allMatch(RbacPermissionEntity::getSystemManaged),
                "内置权限必须全部标记为系统托管");
        Assertions.assertTrue(saved.stream().allMatch(permission -> permission.getAccountId().equals(1L)),
                "权限归属必须以传入的 accountId 为准");
    }

    private RbacPermissionEntity permission(Long id, Long parentId, String code, boolean systemManaged) {
        return RbacPermissionEntity.builder()
                .id(id)
                .accountId(1L)
                .parentId(parentId)
                .permCode(code)
                .systemManaged(systemManaged)
                .status(true)
                .deleted(false)
                .build();
    }
}
