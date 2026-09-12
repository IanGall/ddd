package cn.iantech.domain.rbac.service.impl;

import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.domain.rbac.infra.IRbacAccountRepository;
import cn.iantech.domain.rbac.infra.IRbacAuthorizationRepository;
import cn.iantech.domain.rbac.infra.IRbacPermissionRepository;
import cn.iantech.domain.rbac.infra.IRbacRoleRepository;
import cn.iantech.domain.rbac.model.entity.RbacAccountEntity;
import cn.iantech.domain.rbac.model.entity.RbacPermissionEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RbacPermissionAccessControlTest {

    @Mock
    private IRbacAccountRepository accountRepository;
    @Mock
    private IRbacAuthorizationRepository authorizationRepository;
    @Mock
    private IRbacPermissionRepository permissionRepository;
    @Mock
    private IRbacRoleRepository roleRepository;

    private RbacAccessControlService accessControlService;

    @BeforeEach
    void setUp() {
        accessControlService = new RbacAccessControlService(
                accountRepository, authorizationRepository, permissionRepository, roleRepository);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(RbacAccountEntity.builder().id(1L).build()));
    }

    @Test
    void shouldAllowManagingOwnedCustomPermission() {
        when(authorizationRepository.findPermissionCodes(1L, 2L)).thenReturn(List.of("custom:owned"));
        when(permissionRepository.findById(1L, 10L)).thenReturn(Optional.of(
                RbacPermissionEntity.builder().id(10L).permCode("custom:owned").build()));

        Assertions.assertDoesNotThrow(() ->
                accessControlService.authorizePermissionManagement(1L, 2L, "sub-user", 10L));
    }

    @Test
    void shouldRejectManagingPermissionOutsideDelegation() {
        when(authorizationRepository.findPermissionCodes(1L, 2L)).thenReturn(List.of("custom:owned"));
        when(permissionRepository.findById(1L, 20L)).thenReturn(Optional.of(
                RbacPermissionEntity.builder().id(20L).permCode("custom:not-owned").build()));

        AppException exception = Assertions.assertThrows(AppException.class, () ->
                accessControlService.authorizePermissionManagement(1L, 2L, "sub-user", 20L));

        Assertions.assertEquals(Constants.ResponseCode.ACCESS_DENIED.getCode(), exception.getCode());
    }

    @Test
    void shouldValidateRoleDelegationWithBatchQuery() {
        when(authorizationRepository.existsPermission(1L, 2L, "rbac:user-role:grant")).thenReturn(true);
        when(roleRepository.queryExistingIds(1L, List.of(10L, 20L))).thenReturn(List.of(10L, 20L));
        when(authorizationRepository.findPermissionCodes(1L, 2L))
                .thenReturn(List.of("order:read", "order:update"));
        when(authorizationRepository.findPermissionCodesByRoleIds(1L, List.of(10L, 20L)))
                .thenReturn(List.of("order:read", "order:update"));

        Assertions.assertDoesNotThrow(() ->
                accessControlService.authorizeRoleGrant(1L, 2L, "sub-user", List.of(10L, 20L, 10L)));

        verify(authorizationRepository).findPermissionCodesByRoleIds(1L, List.of(10L, 20L));
        verify(permissionRepository, never()).findById(anyLong(), anyLong());
    }

    @Test
    void shouldRejectCrossAccountRoleBeforePermissionCheck() {
        when(authorizationRepository.existsPermission(1L, 2L, "rbac:user-role:grant")).thenReturn(true);
        when(roleRepository.queryExistingIds(1L, List.of(99L))).thenReturn(List.of());

        AppException exception = Assertions.assertThrows(AppException.class, () ->
                accessControlService.authorizeRoleGrant(1L, 2L, "sub-user", List.of(99L)));

        Assertions.assertEquals(Constants.ResponseCode.ACCESS_DENIED.getCode(), exception.getCode());
        verify(authorizationRepository, never()).findPermissionCodesByRoleIds(anyLong(), anyList());
    }

    @Test
    void shouldRejectPermissionOutsideDelegationWithBatchQuery() {
        when(authorizationRepository.existsPermission(1L, 2L, "rbac:role-permission:grant")).thenReturn(true);
        when(permissionRepository.queryExistingIds(1L, List.of(10L, 20L))).thenReturn(List.of(10L, 20L));
        when(authorizationRepository.findPermissionCodes(1L, 2L)).thenReturn(List.of("order:read"));
        when(authorizationRepository.findPermissionCodesByIds(1L, List.of(10L, 20L)))
                .thenReturn(List.of("order:read", "order:delete"));

        AppException exception = Assertions.assertThrows(AppException.class, () ->
                accessControlService.authorizePermissionGrant(1L, 2L, "sub-user", List.of(10L, 20L)));

        Assertions.assertEquals(Constants.ResponseCode.ACCESS_DENIED.getCode(), exception.getCode());
    }
}
