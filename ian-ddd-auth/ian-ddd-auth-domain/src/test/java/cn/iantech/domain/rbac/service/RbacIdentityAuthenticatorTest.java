package cn.iantech.domain.rbac.service;

import cn.iantech.domain.auth.infra.IPasswordEncoder;
import cn.iantech.domain.rbac.infra.IRbacAccountRepository;
import cn.iantech.domain.rbac.infra.IRbacAuthorizationRepository;
import cn.iantech.domain.rbac.infra.IRbacUserRepository;
import cn.iantech.domain.rbac.model.entity.RbacAccountEntity;
import cn.iantech.domain.rbac.model.entity.RbacUserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RbacIdentityAuthenticatorTest {

    private final IRbacAccountRepository accountRepository = mock(IRbacAccountRepository.class);
    private final IRbacUserRepository userRepository = mock(IRbacUserRepository.class);
    private final IRbacAuthorizationRepository authorizationRepository = mock(IRbacAuthorizationRepository.class);
    private final IPasswordEncoder passwordEncoder = mock(IPasswordEncoder.class);
    private RbacIdentityAuthenticator authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService = new RbacIdentityAuthenticator(
                accountRepository, userRepository, authorizationRepository, passwordEncoder);
    }

    @Test
    void shouldReloadPrimaryAccount() {
        when(accountRepository.findById(1001L)).thenReturn(Optional.of(usableAccount()));

        var result = authenticationService.reload(1001L, 1001L, "PRIMARY");

        assertEquals(1001L, result.accountId());
        assertEquals("root", result.username());
        assertEquals(List.of(), result.permissionCodes());
    }

    @Test
    void shouldReloadSubAccountWithCurrentPermissions() {
        when(accountRepository.findById(1001L)).thenReturn(Optional.of(usableAccount()));
        when(userRepository.findById(1001L, 2001L)).thenReturn(Optional.of(RbacUserEntity.builder()
                .id(2001L).accountId(1001L).username("operator").status(true).deleted(false).build()));
        when(authorizationRepository.findRoleCodes(1001L, 2001L)).thenReturn(List.of("OPERATOR"));
        when(authorizationRepository.findPermissionCodes(1001L, 2001L))
                .thenReturn(List.of("rbac:user:read"));

        var result = authenticationService.reload(1001L, 2001L, "SUB_ACCOUNT");

        assertEquals(List.of("OPERATOR"), result.roleCodes());
        assertEquals(List.of("rbac:user:read"), result.permissionCodes());
    }

    @Test
    void shouldRejectSubAccountWhenOwnerAccountDisabled() {
        when(accountRepository.findById(1001L)).thenReturn(Optional.of(RbacAccountEntity.builder()
                .id(1001L).username("root").status(false).deleted(false).build()));

        assertNull(authenticationService.reload(1001L, 2001L, "SUB_ACCOUNT"));
    }

    @Test
    void shouldRejectForgedUserType() {
        when(accountRepository.findById(1001L)).thenReturn(Optional.of(usableAccount()));

        assertNull(authenticationService.reload(1001L, 1001L, "ADMIN"));
    }

    private RbacAccountEntity usableAccount() {
        return RbacAccountEntity.builder()
                .id(1001L)
                .username("root")
                .status(true)
                .deleted(false)
                .build();
    }
}
