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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 「当前主体有效权限码」查询：主账号取账号内权限目录（不过滤状态，与 authorize 的无条件放行一致）、
 * 子账号走角色聚合（停用与软删除由 SQL 过滤），两路都必须去重升序。
 */
@ExtendWith(MockitoExtension.class)
class RbacEffectivePermissionCodesTest {

    private static final Long ACCOUNT_ID = 1L;

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
    }

    @Test
    void shouldReturnAllPermissionCodesIncludingDisabledOnesForPrimaryAccount() {
        givenUsableAccount();
        when(accountRepository.findByUsername(ACCOUNT_ID, "root")).thenReturn(Optional.of(account()));
        when(permissionRepository.findAllByAccountId(ACCOUNT_ID)).thenReturn(List.of(
                permission("rbac:user:read", true),
                permission("custom:report:read", true),
                permission("rbac:user:delete", false)));

        List<String> codes = accessControlService.findEffectivePermissionCodes(ACCOUNT_ID, ACCOUNT_ID, "root");

        // 主账号没有用户角色关系，权限事实来源是账号内权限目录；且与 authorize 的无条件放行一致，不过滤 status
        Assertions.assertEquals(
                List.of("custom:report:read", "rbac:user:delete", "rbac:user:read"), codes);
        verify(authorizationRepository, never()).findPermissionCodes(any(), any());
    }

    @Test
    void shouldReturnRoleAggregatedPermissionCodesForSubAccount() {
        givenUsableAccount();
        when(authorizationRepository.findPermissionCodes(ACCOUNT_ID, 2L)).thenReturn(
                List.of("rbac:role:read", "rbac:role:read", " ", "rbac:user:read"));

        List<String> codes = accessControlService.findEffectivePermissionCodes(ACCOUNT_ID, 2L, "sub");

        // 空白项被过滤、重复项去重；停用/软删除的过滤由 findPermissionCodes 的 SQL 承担
        Assertions.assertEquals(List.of("rbac:role:read", "rbac:user:read"), codes);
        verify(permissionRepository, never()).findAllByAccountId(any());
    }

    @Test
    void shouldRejectWhenAccountIsMissing() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        AppException exception = Assertions.assertThrows(AppException.class, () ->
                accessControlService.findEffectivePermissionCodes(ACCOUNT_ID, 2L, "sub"));

        Assertions.assertEquals(Constants.ResponseCode.ACCESS_DENIED.getCode(), exception.getCode());
        verifyNoInteractions(authorizationRepository, permissionRepository);
    }

    @Test
    void shouldRejectIllegalArgumentsBeforeTouchingRepositories() {
        Assertions.assertThrows(AppException.class, () ->
                accessControlService.findEffectivePermissionCodes(null, 2L, "sub"));
        Assertions.assertThrows(AppException.class, () ->
                accessControlService.findEffectivePermissionCodes(ACCOUNT_ID, null, "sub"));
        Assertions.assertThrows(AppException.class, () ->
                accessControlService.findEffectivePermissionCodes(ACCOUNT_ID, 2L, " "));

        verifyNoInteractions(accountRepository, authorizationRepository, permissionRepository);
    }

    private void givenUsableAccount() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account()));
    }

    private RbacAccountEntity account() {
        return RbacAccountEntity.builder().id(ACCOUNT_ID).username("root").status(Boolean.TRUE).build();
    }

    private RbacPermissionEntity permission(String code, boolean enabled) {
        return RbacPermissionEntity.builder().accountId(ACCOUNT_ID).permCode(code).status(enabled).build();
    }
}
