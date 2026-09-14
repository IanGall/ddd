package cn.iantech.test.infrastructure.id;

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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 用真实 Redis 执行租约脚本，验证按业务分块后每个业务只能租到本区间内的 Worker ID。
 *
 * <p>命名空间每次运行都不同，因此各业务的 cursor 必定从 1 开始，租到的就是所在区间的第一个
 * Worker ID；这也让断言不依赖上一次运行留下的键。
 *
 * <p>需要可用的 Redis，通过 {@code RUN_REDIS_TESTS=true} 启用。
 */
@SpringBootTest
@ActiveProfiles({"dev", "autotest"})
@EnabledIfEnvironmentVariable(named = "RUN_REDIS_TESTS", matches = "true")
class IdGeneratorWorkerBlockRedisTest {

    private static final String NAMESPACE = "ddd-global-id-itest-" + UUID.randomUUID();

    private static final String PREFIX = "{" + NAMESPACE + "}:worker";

    private static final int BLOCK_SIZE = 64;

    /** auth 服务声明的 5 个业务：块序号 0..4（channel_data_scope 已改为数据库自增，不再占块）。 */
    private static final List<Integer> BLOCK_INDEXES = List.of(0, 1, 2, 3, 4);

    @Resource
    private IRedisService redisService;

    @DynamicPropertySource
    static void idGeneratorNamespace(DynamicPropertyRegistry registry) {
        registry.add("ddd.id-generator.namespace", () -> NAMESPACE);
    }

    @Test
    void shouldLeaseWorkerIdOnlyInsideOwnBusinessBlock() {
        List<String> cleanupKeys = new ArrayList<>(List.of(PREFIX + ":layout"));
        BLOCK_INDEXES.forEach(index -> {
            cleanupKeys.add(PREFIX + ":lease:" + index * BLOCK_SIZE);
            cleanupKeys.add(PREFIX + ":cursor:" + index);
        });

        try {
            assertEquals("10:12", redisService.getString(PREFIX + ":layout"), "位宽键应由所有业务共享");

            for (int index : BLOCK_INDEXES) {
                int blockStart = index * BLOCK_SIZE;
                assertNotNull(redisService.getString(PREFIX + ":lease:" + blockStart),
                        "块 " + index + " 应租到区间起点 " + blockStart);
                assertNotNull(redisService.getString(PREFIX + ":cursor:" + index),
                        "块 " + index + " 应使用独立 cursor 键");
                assertNull(redisService.getString(PREFIX + ":lease:" + (blockStart + 1)),
                        "块 " + index + " 内除起点外不应有本进程的租约");
            }

            assertNull(redisService.getString(PREFIX + ":cursor"), "分块模式不应使用全池 cursor 键");
            assertNull(redisService.getString(PREFIX + ":lease:" + (BLOCK_INDEXES.size() * BLOCK_SIZE)),
                    "已声明区间之外不应有租约");
        } finally {
            cleanupKeys.forEach(redisService::remove);
        }
    }
}
