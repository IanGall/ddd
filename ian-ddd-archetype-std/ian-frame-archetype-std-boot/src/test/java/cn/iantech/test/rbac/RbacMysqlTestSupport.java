package cn.iantech.test.rbac;

import cn.iantech.api.IRbacService;
import cn.iantech.api.model.rbac.*;
import cn.iantech.context.core.ContextAccessor;
import cn.iantech.context.core.ContextScope;
import cn.iantech.context.core.RequestContext;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public abstract class RbacMysqlTestSupport {

    private static final String USER_PREFIX = "it_user_";
    private static final String ROLE_PREFIX = "it_role_";
    private static final String PERMISSION_PREFIX = "it_perm_";

    @Resource
    protected IRbacService rbacService;

    @Resource
    protected JdbcTemplate jdbcTemplate;

    protected String marker;
    private ContextScope contextScope;

    @BeforeEach
    void initMarker() {
        marker = System.currentTimeMillis() + "_" + ThreadLocalRandom.current().nextInt(1000, 10000);
        contextScope = ContextAccessor.open(new RequestContext(
                "mysql-test-" + marker, "test-admin", "1", "1", null, null, null, "gateway", null,
                null, null, null));
    }

    @AfterEach
    void cleanup() {
        try {
            deleteMarkerData();
        } finally {
            contextScope.close();
        }
    }

    protected RbacUserDTO createUser() {
        String suffix = nextSuffix();
        return rbacService.createUser(CreateRbacUserReq.builder()
                .username(USER_PREFIX + marker + "_" + suffix)
                .password("Pwd@" + suffix)
                .displayName("用户_" + suffix)
                .email("user_" + suffix + "@test.com")
                .mobile("1380000" + String.format("%04d", ThreadLocalRandom.current().nextInt(0, 10000)))
                .status(Boolean.TRUE)
                .build());
    }

    protected RbacRoleDTO createRole() {
        String suffix = nextSuffix();
        return rbacService.createRole(CreateRbacRoleReq.builder()
                .roleCode(ROLE_PREFIX + marker + "_" + suffix)
                .roleName("角色_" + suffix)
                .roleDesc("角色描述_" + suffix)
                .status(Boolean.TRUE)
                .build());
    }

    protected RbacPermissionDTO createPermission() {
        String suffix = nextSuffix();
        return rbacService.createPermission(CreateRbacPermissionReq.builder()
                .permCode(PERMISSION_PREFIX + marker + "_" + suffix)
                .permName("权限_" + suffix)
                .permType(2)
                .parentId(0L)
                .path("/api/" + suffix)
                .method("GET")
                .status(Boolean.TRUE)
                .build());
    }

    protected String markerKeyword() {
        return marker;
    }

    protected void switchActor(Long accountId, Long userId, String principalName) {
        contextScope.close();
        contextScope = ContextAccessor.open(new RequestContext(
                "mysql-test-" + marker, principalName, accountId.toString(), userId.toString(), null, null, null,
                "gateway", null, null, null, null));
    }

    protected void switchActor(Long accountId, String principalName) {
        switchActor(accountId, accountId, principalName);
    }

    private String nextSuffix() {
        return Long.toString(ThreadLocalRandom.current().nextLong(100000, 999999));
    }

    private void deleteMarkerData() {
        List<Long> userIds = queryIdsByLike("SELECT id FROM rbac_user WHERE account_id = 1 AND username LIKE ?", USER_PREFIX + marker + "%");
        List<Long> roleIds = queryIdsByLike("SELECT id FROM rbac_role WHERE account_id = 1 AND role_code LIKE ?", ROLE_PREFIX + marker + "%");
        List<Long> permissionIds = queryIdsByLike("SELECT id FROM rbac_permission WHERE account_id = 1 AND perm_code LIKE ?", PERMISSION_PREFIX + marker + "%");

        deleteByIds("DELETE FROM rbac_user_role WHERE account_id = 1 AND user_id IN (%s)", userIds);
        deleteByIds("DELETE FROM rbac_user_role WHERE account_id = 1 AND role_id IN (%s)", roleIds);
        deleteByIds("DELETE FROM rbac_role_permission WHERE account_id = 1 AND role_id IN (%s)", roleIds);
        deleteByIds("DELETE FROM rbac_role_permission WHERE account_id = 1 AND permission_id IN (%s)", permissionIds);
        deleteByIds("DELETE FROM rbac_user WHERE account_id = 1 AND id IN (%s)", userIds);
        deleteByIds("DELETE FROM rbac_role WHERE account_id = 1 AND id IN (%s)", roleIds);
        deleteByIds("DELETE FROM rbac_permission WHERE account_id = 1 AND id IN (%s)", permissionIds);
    }

    private List<Long> queryIdsByLike(String sql, String likeValue) {
        return jdbcTemplate.queryForList(sql, Long.class, likeValue);
    }

    private void deleteByIds(String sqlTemplate, List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = ids.stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));
        jdbcTemplate.update(sqlTemplate.formatted(placeholders), ids.toArray());
    }

}
