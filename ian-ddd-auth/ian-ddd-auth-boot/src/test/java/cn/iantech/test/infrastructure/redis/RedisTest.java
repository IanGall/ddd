package cn.iantech.test.infrastructure.persistent;

import cn.iantech.redis.IRedisService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Redis 案例；<a href="https://iantech.cn/md/road-map/redis.html">Redis</a>
 */
@Slf4j
@SpringBootTest
@ActiveProfiles({"dev", "autotest"})
@EnabledIfEnvironmentVariable(named = "RUN_REDIS_TESTS", matches = "true")
public class RedisTest {

    private static final String TEST_KEY = "ian:test:redis-case";

    @Resource
    private IRedisService redissonService;

    // 验证 Redis 字符串值写入后可读回
    @Test
    public void shouldSetRedisValue() {
        redissonService.setValue(TEST_KEY, "test123");
        String value = redissonService.getValue(TEST_KEY);
        log.info("设置属性值，读回：{}", value);
        Assertions.assertEquals("test123", value);
    }

    // 验证 Redis 字符串值读取
    @Test
    public void shouldGetRedisValue() {
        redissonService.setValue(TEST_KEY, "read-me");
        String value = redissonService.getValue(TEST_KEY);
        log.info("测试结果:{}", value);
        Assertions.assertEquals("read-me", value);
    }

    // 验证 Redis 键删除后不可读
    @Test
    public void shouldRemoveRedisValue() {
        redissonService.setValue(TEST_KEY, "to-be-removed");
        Assertions.assertEquals("to-be-removed", redissonService.getValue(TEST_KEY));
        redissonService.remove(TEST_KEY);
        Assertions.assertNull(redissonService.getValue(TEST_KEY));
    }

}
