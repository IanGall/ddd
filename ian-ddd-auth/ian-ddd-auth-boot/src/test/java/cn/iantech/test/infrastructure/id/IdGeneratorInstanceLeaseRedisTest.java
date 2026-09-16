package cn.iantech.test.infrastructure.id;

import cn.iantech.id.GlobalIdGeneratorProvider;
import cn.iantech.id.IdGenerationException;
import cn.iantech.redis.IRedisService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 用真实 Redis 执行租约脚本，验证 Worker ID 是**实例级**资源：一个实例只租一个，
 * 实例内所有业务共用它。
 *
 * <p>命名空间每次运行都不同，因此 cursor 必定从 1 开始，租到的就是池内第一个空闲槽位 0；
 * 这也让断言不依赖上一次运行留下的键。
 *
 * <p>需要可用的 Redis，通过 {@code RUN_REDIS_TESTS=true} 启用。
 */
@SpringBootTest
@ActiveProfiles({"dev", "autotest"})
@EnabledIfEnvironmentVariable(named = "RUN_REDIS_TESTS", matches = "true")
class IdGeneratorInstanceLeaseRedisTest {

    private static final String NAMESPACE = "ddd-global-id-itest-" + UUID.randomUUID();

    private static final String PREFIX = "{" + NAMESPACE + "}:worker";

    /** auth 服务声明的 4 个业务，全部共用一个实例级 Worker ID。 */
    private static final List<String> BUSINESSES =
            List.of("identity", "auth-session", "channel-credential", "user-order");

    @Resource
    private IRedisService redisService;

    @Resource
    private GlobalIdGeneratorProvider idGeneratorProvider;

    @DynamicPropertySource
    static void idGeneratorNamespace(DynamicPropertyRegistry registry) {
        registry.add("ddd.id-generator.namespace", () -> NAMESPACE);
    }

    @Test
    void shouldLeaseExactlyOneInstanceLevelWorkerIdSharedByAllBusinesses() {
        try {
            assertEquals("10", redisService.getString(PREFIX + ":pool"),
                    "服务级守卫只记录 workerIdBitLength");
            BUSINESSES.forEach(business -> assertEquals("10:12",
                    redisService.getString(PREFIX + ":layout:" + business),
                    "业务 " + business + " 应记录自己的 ID 位布局"));

            assertNotNull(redisService.getString(PREFIX + ":lease:0"), "cursor 从 1 起，应租到槽位 0");
            assertNull(redisService.getString(PREFIX + ":lease:1"),
                    "全部业务共用一个 Worker ID，一个实例只应占一个槽位");

            assertNull(redisService.getString(PREFIX + ":layout"), "不应再使用旧的单值位宽键");
            assertNull(redisService.getString(PREFIX + ":cursor:0"), "不应再使用按业务分块的 cursor 键");
        } finally {
            cleanup();
        }
    }

    @Test
    void shouldMergeIdentityTablesIntoOneBusiness() {
        // 身份类三表已合并为 identity：旧业务名必须不再存在
        assertThrows(IdGenerationException.class, () -> idGeneratorProvider.forBusiness("rbac-account"));
        assertThrows(IdGenerationException.class, () -> idGeneratorProvider.forBusiness("rbac-user"));
        assertThrows(IdGenerationException.class, () -> idGeneratorProvider.forBusiness("customer-user"));

        // 同一业务始终返回同一实例，因此三个身份 Repository 共用一把序列
        assertEquals(idGeneratorProvider.forBusiness("identity"),
                idGeneratorProvider.forBusiness("identity"));
    }

    @Test
    void shouldKeepIdsUniqueWithinOneBusiness() {
        Set<Long> ids = IntStream.range(0, 5_000)
                .mapToObj(ignored -> idGeneratorProvider.forBusiness("identity").nextId())
                .collect(Collectors.toSet());

        assertEquals(5_000, ids.size(), "业务域内必须唯一");
    }

    private void cleanup() {
        List<String> keys = new ArrayList<>(List.of(PREFIX + ":pool", PREFIX + ":cursor"));
        BUSINESSES.forEach(business -> keys.add(PREFIX + ":layout:" + business));
        IntStream.range(0, 8).forEach(slot -> keys.add(PREFIX + ":lease:" + slot));
        keys.forEach(redisService::remove);
    }
}
