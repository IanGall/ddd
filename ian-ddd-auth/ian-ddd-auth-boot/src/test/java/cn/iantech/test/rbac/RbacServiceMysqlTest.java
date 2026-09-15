package cn.iantech.test.rbac;

import cn.iantech.IanDddAuthApplication;
import cn.iantech.api.IAuthService;
import cn.iantech.api.IRbacService;
import cn.iantech.api.model.auth.AuthLoginReq;
import cn.iantech.api.model.rbac.*;
import cn.iantech.cases.rbac.service.RbacCaseService;
import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.domain.auth.service.IAdminIdentityAuthenticator;
import cn.iantech.id.GlobalIdGeneratorProvider;
import cn.iantech.redis.IRedisService;
import cn.iantech.trigger.context.ActorResolver;
import cn.iantech.trigger.convertor.RbacCommandConvertor;
import cn.iantech.trigger.rpc.RbacService;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import io.github.linpeilie.Converter;
import jakarta.annotation.Resource;
import org.mockito.Mockito;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.function.Executable;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

@SpringBootTest(classes = IanDddAuthApplication.class)
@ActiveProfiles("rbac-mysql-test")
@EnabledIfEnvironmentVariable(named = "RUN_RBAC_MYSQL_TESTS", matches = "true")
@Import(RbacServiceMysqlTest.RbacServiceTestConfiguration.class)
public class RbacServiceMysqlTest extends RbacMysqlTestSupport {

    @MockitoBean(name = "xxlJobExecutor")
    private XxlJobSpringExecutor xxlJobSpringExecutor;

    @Resource
    private IAuthService authService;

    @Resource
    private IAdminIdentityAuthenticator authenticationService;

    @Test
    void shouldAuthenticatePrimaryAccountWithoutReturningPassword() {
        var auth = authService.login(AuthLoginReq.builder()
                .loginName("test-admin@1.com")
                .password("test-password")
                .build());

        Assertions.assertNotNull(auth.getIdentity().getUserId());
        Assertions.assertEquals(1L, auth.getIdentity().getAccountId());
        Assertions.assertEquals("test-admin", auth.getIdentity().getUsername());
        Assertions.assertEquals("PRIMARY", auth.getIdentity().getUserType());
    }

    @Test
    void shouldReloadCurrentAuthenticationWithoutPassword() {
        RbacUserDTO user = createUser();

        var auth = authenticationService.reload(1L, user.getId(), "SUB_ACCOUNT");

        Assertions.assertNotNull(auth);
        Assertions.assertEquals(user.getUsername(), auth.username());
        Assertions.assertEquals("SUB_ACCOUNT", auth.userType());
    }

    @Test
    void shouldRunUserCrudAndQueryPage() {
        RbacUserDTO createdUser = createUser();
        Assertions.assertNotNull(createdUser.getId());

        RbacUserDTO queriedUser = rbacService.queryUserById(createdUser.getId());
        Assertions.assertEquals(createdUser.getUsername(), queriedUser.getUsername());

        RbacUserPageDTO pageDTO = rbacService.queryUserPage(QueryRbacUserPageReq.builder()
                .pageNum(1)
                .pageSize(20)
                .username(markerKeyword())
                .status(Boolean.TRUE)
                .build());
        Assertions.assertTrue(pageDTO.getList().stream().anyMatch(item -> item.getId().equals(createdUser.getId())));

        RbacUserDTO updatedUser = rbacService.updateUser(UpdateRbacUserReq.builder()
                .id(createdUser.getId())
                .displayName("更新用户_" + markerKeyword())
                .email("updated_" + markerKeyword() + "@test.com")
                .mobile("13900001111")
                .status(Boolean.FALSE)
                .build());
        Assertions.assertEquals("更新用户_" + markerKeyword(), updatedUser.getDisplayName());
        Assertions.assertFalse(updatedUser.getStatus());

        Assertions.assertTrue(rbacService.deleteUser(DeleteRbacUserReq.builder().id(createdUser.getId()).build()));
        assertIllegalParameter(() -> rbacService.queryUserById(createdUser.getId()));
    }

    @Test
    void shouldRunRoleCrudAndQueryPage() {
        RbacRoleDTO createdRole = createRole();
        Assertions.assertNotNull(createdRole.getId());

        RbacRoleDTO queriedRole = rbacService.queryRoleById(createdRole.getId());
        Assertions.assertEquals(createdRole.getRoleCode(), queriedRole.getRoleCode());

        RbacRolePageDTO pageDTO = rbacService.queryRolePage(QueryRbacRolePageReq.builder()
                .pageNum(1)
                .pageSize(20)
                .roleCode(markerKeyword())
                .status(Boolean.TRUE)
                .build());
        Assertions.assertTrue(pageDTO.getList().stream().anyMatch(item -> item.getId().equals(createdRole.getId())));

        String updatedRoleCode = "it_role_" + markerKeyword() + "_updated";
        RbacRoleDTO updatedRole = rbacService.updateRole(UpdateRbacRoleReq.builder()
                .id(createdRole.getId())
                .roleCode(updatedRoleCode)
                .roleName("更新角色_" + markerKeyword())
                .roleDesc("更新描述_" + markerKeyword())
                .status(Boolean.FALSE)
                .build());
        Assertions.assertEquals(updatedRoleCode, updatedRole.getRoleCode());
        Assertions.assertFalse(updatedRole.getStatus());

        Assertions.assertTrue(rbacService.deleteRole(DeleteRbacRoleReq.builder().id(createdRole.getId()).build()));
        assertIllegalParameter(() -> rbacService.queryRoleById(createdRole.getId()));
    }

    @Test
    void shouldRunPermissionCrudAndQueryPage() {
        RbacPermissionDTO createdPermission = createPermission();
        Assertions.assertNotNull(createdPermission.getId());

        RbacPermissionDTO queriedPermission = rbacService.queryPermissionById(createdPermission.getId());
        Assertions.assertEquals(createdPermission.getPermCode(), queriedPermission.getPermCode());

        RbacPermissionPageDTO pageDTO = rbacService.queryPermissionPage(QueryRbacPermissionPageReq.builder()
                .pageNum(1)
                .pageSize(20)
                .permCode(markerKeyword())
                .permType(2)
                .status(Boolean.TRUE)
                .build());
        Assertions.assertTrue(pageDTO.getList().stream().anyMatch(item -> item.getId().equals(createdPermission.getId())));

        RbacPermissionDTO updatedPermission = rbacService.updatePermission(UpdateRbacPermissionReq.builder()
                .id(createdPermission.getId())
                .permName("更新权限_" + markerKeyword())
                .permType(3)
                .parentId(0L)
                .path("/updated/" + markerKeyword())
                .method("POST")
                .status(Boolean.FALSE)
                .build());
        Assertions.assertEquals(createdPermission.getPermCode(), updatedPermission.getPermCode());
        Assertions.assertEquals(3, updatedPermission.getPermType());

        Assertions.assertTrue(rbacService.deletePermission(DeleteRbacPermissionReq.builder().id(createdPermission.getId()).build()));
        assertIllegalParameter(() -> rbacService.queryPermissionById(createdPermission.getId()));
    }

    @Test
    void shouldMaintainUserRoleRelation() {
        RbacUserDTO user = createUser();
        RbacRoleDTO role1 = createRole();
        RbacRoleDTO role2 = createRole();

        Assertions.assertTrue(rbacService.replaceUserRoles(ReplaceUserRolesReq.builder()
                .userId(user.getId())
                .roleIds(List.of(role1.getId(), role2.getId(), role2.getId()))
                .build()));

        QueryUserRoleIdsResp firstQueryResult = rbacService.queryUserRoleIds(QueryUserRoleIdsReq.builder()
                .userId(user.getId())
                .build());
        Assertions.assertEquals(Set.of(role1.getId(), role2.getId()), Set.copyOf(firstQueryResult.getRoleIds()));

        Assertions.assertTrue(rbacService.replaceUserRoles(ReplaceUserRolesReq.builder()
                .userId(user.getId())
                .roleIds(List.of(role2.getId()))
                .build()));
        QueryUserRoleIdsResp secondQueryResult = rbacService.queryUserRoleIds(QueryUserRoleIdsReq.builder()
                .userId(user.getId())
                .build());
        Assertions.assertEquals(List.of(role2.getId()), secondQueryResult.getRoleIds());
    }

    @Test
    void shouldMaintainRolePermissionRelation() {
        RbacRoleDTO role = createRole();
        RbacPermissionDTO permission1 = createPermission();
        RbacPermissionDTO permission2 = createPermission();

        Assertions.assertTrue(rbacService.replaceRolePermissions(ReplaceRolePermissionsReq.builder()
                .roleId(role.getId())
                .permissionIds(List.of(permission1.getId(), permission2.getId(), permission1.getId()))
                .build()));

        QueryRolePermissionIdsResp firstQueryResult = rbacService.queryRolePermissionIds(QueryRolePermissionIdsReq.builder()
                .roleId(role.getId())
                .build());
        Assertions.assertEquals(Set.of(permission1.getId(), permission2.getId()), Set.copyOf(firstQueryResult.getPermissionIds()));

        Assertions.assertTrue(rbacService.replaceRolePermissions(ReplaceRolePermissionsReq.builder()
                .roleId(role.getId())
                .permissionIds(List.of(permission2.getId()))
                .build()));
        QueryRolePermissionIdsResp secondQueryResult = rbacService.queryRolePermissionIds(QueryRolePermissionIdsReq.builder()
                .roleId(role.getId())
                .build());
        Assertions.assertEquals(List.of(permission2.getId()), secondQueryResult.getPermissionIds());
    }

    // 权限引导端点：主账号拿账号内全部权限（含停用项与自定义权限），子账号拿角色聚合结果且过滤停用项，
    // 并且都不要求调用者自身有权限
    @Test
    void shouldReturnOwnPermissionCodesForPrimaryAndSubAccount() {
        RbacPermissionDTO enabledPermission = createPermission();
        RbacPermissionDTO disabledPermission = createPermission();
        rbacService.updatePermission(UpdateRbacPermissionReq.builder()
                .id(disabledPermission.getId())
                .permName("停用权限")
                .permType(2)
                .parentId(0L)
                .status(Boolean.FALSE)
                .build());

        List<String> primaryCodes = rbacService.queryOwnPermissionCodes();
        // 主账号与 authorize 的无条件放行一致：权限事实来源是账号内权限目录，且不过滤 status
        Assertions.assertTrue(primaryCodes.contains(enabledPermission.getPermCode()),
                "主账号应包含账号内的自定义权限码");
        Assertions.assertTrue(primaryCodes.contains(disabledPermission.getPermCode()),
                "主账号不得因权限停用而丢失权限码（否则会出现「接口能调通、菜单不显示」的错位）");
        Assertions.assertEquals(primaryCodes.stream().distinct().sorted().toList(), primaryCodes,
                "权限码必须去重且升序");

        RbacUserDTO user = createUser();
        RbacRoleDTO role = createRole();
        Assertions.assertTrue(rbacService.replaceRolePermissions(ReplaceRolePermissionsReq.builder()
                .roleId(role.getId())
                .permissionIds(List.of(enabledPermission.getId(), disabledPermission.getId()))
                .build()));

        // 子账号此时没有任何权限码，调用仍成功——证明该端点不要求调用者自身持有权限码
        switchActor(1L, user.getId(), user.getUsername());
        Assertions.assertEquals(List.of(), rbacService.queryOwnPermissionCodes());

        // 授予角色后拿到角色聚合结果：停用权限被 SQL 过滤，只剩启用中的那一项
        switchActor(1L, "test-admin");
        Assertions.assertTrue(rbacService.replaceUserRoles(ReplaceUserRolesReq.builder()
                .userId(user.getId())
                .roleIds(List.of(role.getId()))
                .build()));
        switchActor(1L, user.getId(), user.getUsername());
        Assertions.assertEquals(List.of(enabledPermission.getPermCode()), rbacService.queryOwnPermissionCodes());
    }

    @Test
    void shouldThrowWhenRequestIsNull() {
        assertIllegalParameter(() -> rbacService.createUser(null));
        assertIllegalParameter(() -> rbacService.createRole(null));
        assertIllegalParameter(() -> rbacService.createPermission(null));
        assertIllegalParameter(() -> rbacService.replaceUserRoles(null));
        assertIllegalParameter(() -> rbacService.replaceRolePermissions(null));
        assertIllegalParameter(() -> rbacService.queryUserRoleIds(null));
        assertIllegalParameter(() -> rbacService.queryRolePermissionIds(null));
    }

    @Test
    void shouldThrowWhenIdIllegal() {
        assertIllegalParameter(() -> rbacService.queryUserById(0L));
        assertIllegalParameter(() -> rbacService.queryRoleById(-1L));
        assertIllegalParameter(() -> rbacService.queryPermissionById(0L));
        assertIllegalParameter(() -> rbacService.deleteUser(DeleteRbacUserReq.builder().id(0L).build()));
        assertIllegalParameter(() -> rbacService.deleteRole(DeleteRbacRoleReq.builder().id(0L).build()));
        assertIllegalParameter(() -> rbacService.deletePermission(DeleteRbacPermissionReq.builder().id(0L).build()));
        assertIllegalParameter(() -> rbacService.queryUserRoleIds(QueryUserRoleIdsReq.builder().userId(0L).build()));
        assertIllegalParameter(() -> rbacService.queryRolePermissionIds(QueryRolePermissionIdsReq.builder().roleId(0L).build()));
    }

    @Test
    void shouldThrowWhenCodeDuplicated() {
        String duplicateUserName = "it_user_" + markerKeyword() + "_dup";
        rbacService.createUser(CreateRbacUserReq.builder()
                .username(duplicateUserName)
                .password("Pwd@0011")
                .displayName("重复用户")
                .email("dup_user@test.com")
                .mobile("13800000001")
                .status(Boolean.TRUE)
                .build());
        assertIllegalParameter(() -> rbacService.createUser(CreateRbacUserReq.builder()
                .username(duplicateUserName)
                .password("Pwd@0022")
                .displayName("重复用户2")
                .email("dup_user2@test.com")
                .mobile("13800000002")
                .status(Boolean.TRUE)
                .build()));

        String duplicateRoleCode = "it_role_" + markerKeyword() + "_dup";
        rbacService.createRole(CreateRbacRoleReq.builder()
                .roleCode(duplicateRoleCode)
                .roleName("重复角色")
                .roleDesc("desc")
                .status(Boolean.TRUE)
                .build());
        assertIllegalParameter(() -> rbacService.createRole(CreateRbacRoleReq.builder()
                .roleCode(duplicateRoleCode)
                .roleName("重复角色2")
                .roleDesc("desc2")
                .status(Boolean.TRUE)
                .build()));

        String duplicatePermCode = "it_perm_" + markerKeyword() + "_dup";
        rbacService.createPermission(CreateRbacPermissionReq.builder()
                .permCode(duplicatePermCode)
                .permName("重复权限")
                .permType(2)
                .parentId(0L)
                .path("/dup")
                .method("GET")
                .status(Boolean.TRUE)
                .build());
        assertIllegalParameter(() -> rbacService.createPermission(CreateRbacPermissionReq.builder()
                .permCode(duplicatePermCode)
                .permName("重复权限2")
                .permType(2)
                .parentId(0L)
                .path("/dup2")
                .method("POST")
                .status(Boolean.TRUE)
                .build()));
    }

    @Test
    void shouldThrowWhenPermissionTypeIllegal() {
        assertIllegalParameter(() -> rbacService.createPermission(CreateRbacPermissionReq.builder()
                .permCode("it_perm_" + markerKeyword() + "_illegal")
                .permName("非法权限类型")
                .permType(9)
                .parentId(0L)
                .path("/illegal")
                .method("GET")
                .status(Boolean.TRUE)
                .build()));

        RbacPermissionDTO permission = createPermission();
        assertIllegalParameter(() -> rbacService.updatePermission(UpdateRbacPermissionReq.builder()
                .id(permission.getId())
                .permType(9)
                .build()));
    }

    @Test
    void shouldThrowWhenUpdateContentEmpty() {
        RbacUserDTO user = createUser();
        RbacRoleDTO role = createRole();
        RbacPermissionDTO permission = createPermission();

        assertIllegalParameter(() -> rbacService.updateUser(UpdateRbacUserReq.builder().id(user.getId()).build()));
        assertIllegalParameter(() -> rbacService.updateRole(UpdateRbacRoleReq.builder().id(role.getId()).build()));
        assertIllegalParameter(() -> rbacService.updatePermission(UpdateRbacPermissionReq.builder().id(permission.getId()).build()));
    }

    @Test
    void shouldQueryPagesByMarker() {
        IntStream.range(0, 3).forEach(index -> createUser());
        IntStream.range(0, 3).forEach(index -> createRole());
        IntStream.range(0, 3).forEach(index -> createPermission());

        RbacUserPageDTO userPage = rbacService.queryUserPage(QueryRbacUserPageReq.builder()
                .pageNum(1)
                .pageSize(10)
                .username(markerKeyword())
                .build());
        RbacRolePageDTO rolePage = rbacService.queryRolePage(QueryRbacRolePageReq.builder()
                .pageNum(1)
                .pageSize(10)
                .roleCode(markerKeyword())
                .build());
        RbacPermissionPageDTO permissionPage = rbacService.queryPermissionPage(QueryRbacPermissionPageReq.builder()
                .pageNum(1)
                .pageSize(10)
                .permCode(markerKeyword())
                .build());

        Assertions.assertTrue(userPage.getTotal() >= 3);
        Assertions.assertTrue(rolePage.getTotal() >= 3);
        Assertions.assertTrue(permissionPage.getTotal() >= 3);
    }

    // 验证同名资源可按账号创建，且查询、关系绑定和父权限均不能跨账号
    @Test
    void shouldIsolateResourcesAndRelationsBetweenAccounts() {
        long secondTenantId = ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE);
        String secondPrincipal = "tenant_admin_" + markerKeyword();

        try {
            initializeTenantAdmin(secondTenantId, secondPrincipal);
            String sharedUsername = "it_user_" + markerKeyword() + "_shared";
            String sharedRoleCode = "it_role_" + markerKeyword() + "_shared";
            String sharedPermissionCode = "it_perm_" + markerKeyword() + "_shared";

            RbacUserDTO firstUser = createNamedUser(sharedUsername);
            RbacRoleDTO firstRole = createNamedRole(sharedRoleCode);
            RbacPermissionDTO firstPermission = createNamedPermission(sharedPermissionCode, 0L);

            switchActor(secondTenantId, secondPrincipal);
            RbacUserDTO secondUser = createNamedUser(sharedUsername);
            RbacRoleDTO secondRole = createNamedRole(sharedRoleCode);
            RbacPermissionDTO secondPermission = createNamedPermission(sharedPermissionCode, 0L);

            Assertions.assertNotEquals(firstUser.getId(), secondUser.getId());
            Assertions.assertNotEquals(firstRole.getId(), secondRole.getId());
            Assertions.assertNotEquals(firstPermission.getId(), secondPermission.getId());
            assertIllegalParameter(() -> rbacService.queryUserById(firstUser.getId()));
            assertIllegalParameter(() -> rbacService.replaceUserRoles(ReplaceUserRolesReq.builder()
                    .userId(secondUser.getId()).roleIds(List.of(firstRole.getId())).build()));
            assertIllegalParameter(() -> createNamedPermission(
                    "it_perm_" + markerKeyword() + "_cross_parent", firstPermission.getId()));

            switchActor(1L, "test-admin");
            assertIllegalParameter(() -> rbacService.queryRoleById(secondRole.getId()));
            assertIllegalParameter(() -> rbacService.queryPermissionById(secondPermission.getId()));
        } finally {
            deleteTenantData(secondTenantId);
            switchActor(1L, "test-admin");
        }
    }

    private void initializeTenantAdmin(long accountId, String principalName) {
        jdbcTemplate.update("INSERT INTO rbac_account "
                        + "(id, username, password_hash, display_name, status, deleted) VALUES (?, ?, ?, ?, 1, 0)",
                accountId, principalName, "test-password-hash", "隔离测试主账号_" + markerKeyword());
    }

    private RbacUserDTO createNamedUser(String username) {
        return rbacService.createUser(CreateRbacUserReq.builder()
                .username(username).password("Pwd@0011").displayName("同名用户").status(Boolean.TRUE).build());
    }

    private RbacRoleDTO createNamedRole(String roleCode) {
        return rbacService.createRole(CreateRbacRoleReq.builder()
                .roleCode(roleCode).roleName("同名角色").status(Boolean.TRUE).build());
    }

    private RbacPermissionDTO createNamedPermission(String permissionCode, Long parentId) {
        return rbacService.createPermission(CreateRbacPermissionReq.builder()
                .permCode(permissionCode).permName("同名权限").permType(2).parentId(parentId)
                .path("/tenant/isolation").method("GET").status(Boolean.TRUE).build());
    }

    private void deleteTenantData(long accountId) {
        jdbcTemplate.update("DELETE FROM rbac_role_permission WHERE account_id = ?", accountId);
        jdbcTemplate.update("DELETE FROM rbac_user_role WHERE account_id = ?", accountId);
        jdbcTemplate.update("DELETE FROM rbac_permission WHERE account_id = ?", accountId);
        jdbcTemplate.update("DELETE FROM rbac_role WHERE account_id = ?", accountId);
        jdbcTemplate.update("DELETE FROM rbac_user WHERE account_id = ?", accountId);
        jdbcTemplate.update("DELETE FROM rbac_account WHERE id = ?", accountId);
    }

    private void assertIllegalParameter(Executable executable) {
        AppException exception = Assertions.assertThrows(AppException.class, executable);
        Assertions.assertEquals(Constants.ResponseCode.INVALID_ARGUMENT.getCode(), exception.getCode());
    }

    @TestConfiguration
    static class RbacServiceTestConfiguration {

        @Bean
        public GlobalIdGeneratorProvider globalIdGeneratorProvider() {
            return new FixedGlobalIdGeneratorProvider();
        }

        /**
         * 用例不依赖真实 Redis，但 Redis starter 的 @ConditionalOnBean 只能看到普通 Bean 定义，
         * @MockitoBean 注册的替身对它不可见，因此这里显式声明替身。
         */
        @Bean
        @Primary
        public RedissonClient redissonClient() {
            return Mockito.mock(RedissonClient.class);
        }

        @Bean
        @Primary
        public IRedisService redisService() {
            IRedisService redisService = Mockito.mock(IRedisService.class);
            Mockito.when(redisService.executeLongScript(Mockito.anyString(), Mockito.anyList(), Mockito.anyList()))
                    .thenReturn(1L);
            return redisService;
        }

        @Bean
        @Primary
        public IRbacService rbacService(RbacCaseService caseService, ActorResolver actorResolver,
                                        Converter converter, RbacCommandConvertor commandConvertor) {
            return new RbacService(caseService, actorResolver, converter, commandConvertor);
        }

    }

}
