package cn.iantech.test.rbac;

import cn.iantech.IanDddAuthApplication;
import cn.iantech.api.IRbacService;
import cn.iantech.api.model.rbac.CreateRbacPermissionReq;
import cn.iantech.api.model.rbac.QueryRbacUserPageReq;
import cn.iantech.api.model.rbac.RbacPermissionDTO;
import cn.iantech.api.model.rbac.RbacUserDTO;
import cn.iantech.api.model.rbac.RbacUserPageDTO;
import cn.iantech.cases.rbac.service.RbacCaseService;
import cn.iantech.id.GlobalIdGeneratorProvider;
import cn.iantech.redis.IRedisService;
import cn.iantech.test.nplusone.DetectNPlusOne;
import cn.iantech.trigger.context.ActorResolver;
import cn.iantech.trigger.convertor.RbacCommandConvertor;
import cn.iantech.trigger.rpc.RbacService;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import io.github.linpeilie.Converter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * RBAC 服务调用的 N+1 回归防护。
 *
 * <p>夹具全部用 JdbcTemplate 直接写入（INSERT 不计入 SELECT），因此观测到的查询只来自被测服务调用。
 * 断言分两层：{@code maxRepeatedSelects} 默认 1 拦截「逐元素重复同一语句」的典型 N+1；
 * {@code maxSelects} 锁定单次调用的查询总数，拦截查询数随数据量放大的实现。</p>
 */
@SpringBootTest(classes = IanDddAuthApplication.class)
@ActiveProfiles("rbac-mysql-test")
@EnabledIfEnvironmentVariable(named = "RUN_RBAC_MYSQL_TESTS", matches = "true")
@Import(RbacNPlusOneMysqlTest.NPlusOneTestConfiguration.class)
class RbacNPlusOneMysqlTest extends RbacMysqlTestSupport {

    @MockitoBean(name = "redissonClient")
    private RedissonClient redissonClient;

    @MockitoBean(name = "xxlJobExecutor")
    private XxlJobSpringExecutor xxlJobSpringExecutor;

    @Test
    @DetectNPlusOne(maxSelects = 3)
    void shouldQueryUserByIdWithinSelectBudget() {
        Long userId = insertUser("it_user_" + markerKeyword() + "_n1");

        RbacUserDTO user = rbacService.queryUserById(userId);

        Assertions.assertEquals(userId, user.getId());
    }

    @Test
    @DetectNPlusOne(maxSelects = 4)
    void shouldCheckPermissionParentChainWithoutPerLevelQueries() {
        Long rootId = insertPermission("it_perm_" + markerKeyword() + "_root", 0L);
        Long childId = insertPermission("it_perm_" + markerKeyword() + "_child", rootId);
        Long grandChildId = insertPermission("it_perm_" + markerKeyword() + "_grandchild", childId);

        RbacPermissionDTO created = rbacService.createPermission(CreateRbacPermissionReq.builder()
                .permCode("it_perm_" + markerKeyword() + "_leaf")
                .permName("N+1 叶子权限")
                .permType(2)
                .parentId(grandChildId)
                .path("/n1/leaf")
                .method("GET")
                .status(Boolean.TRUE)
                .build());

        Assertions.assertEquals(grandChildId, created.getParentId());
    }

    @Test
    @DetectNPlusOne(maxSelects = 4)
    void shouldQueryUserPageWithoutPerRowQueries() {
        IntStream.range(0, 3).forEach(index -> insertUser("it_user_" + markerKeyword() + "_page" + index));

        RbacUserPageDTO page = rbacService.queryUserPage(QueryRbacUserPageReq.builder()
                .pageNum(1)
                .pageSize(10)
                .username(markerKeyword())
                .build());

        Assertions.assertEquals(3, page.getList().size());
    }

    /**
     * 直接落库造数：用户名沿用 {@code it_user_} 前缀，便于基座用例结束后清理。
     */
    private Long insertUser(String username) {
        long userId = ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE);
        jdbcTemplate.update("INSERT INTO rbac_user "
                        + "(id, account_id, username, password_hash, display_name, email, mobile, status, deleted) "
                        + "VALUES (?, 1, ?, ?, ?, ?, ?, 1, 0)",
                userId, username, "test-password-hash", "N+1 用户", "n1_user@test.com", "13800000000");
        return userId;
    }

    private Long insertPermission(String permCode, Long parentId) {
        long permissionId = ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE);
        jdbcTemplate.update("INSERT INTO rbac_permission "
                        + "(id, account_id, perm_code, perm_name, perm_type, parent_id, path, method, status, "
                        + "system_managed, deleted) VALUES (?, 1, ?, ?, 2, ?, ?, 'GET', 1, 0, 0)",
                permissionId, permCode, "N+1 权限", parentId, "/n1/" + permissionId);
        return permissionId;
    }

    @TestConfiguration
    static class NPlusOneTestConfiguration {

        /**
         * 固定自增 ID，避免用例依赖 Redis 租约的全局 ID 生成器。
         */
        @Bean
        public GlobalIdGeneratorProvider globalIdGeneratorProvider() {
            return new FixedGlobalIdGeneratorProvider();
        }

        @Bean
        @Primary
        public IRbacService rbacService(RbacCaseService caseService, ActorResolver actorResolver,
                                        Converter converter, RbacCommandConvertor commandConvertor) {
            return new RbacService(caseService, actorResolver, converter, commandConvertor);
        }

        /**
         * 被测的 RBAC 调用只走数据库，用替身提供会话能力使上下文在无 Redis 时可启动。
         */
        @Bean
        @Primary
        public IRedisService redisService() {
            return Mockito.mock(IRedisService.class);
        }
    }
}
