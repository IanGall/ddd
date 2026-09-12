package cn.iantech.test.infrastructure.persistent;

import cn.iantech.redis.IRedisService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
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

    @Resource
    private IRedisService redissonService;

    // 验证 Redis 字符串值写入
    @Test
    public void shouldSetRedisValue() {
        redissonService.setValue("ian", "test123");
        log.info("设置属性值");
    }

    // 验证 Redis 字符串值读取
    @Test
    public void shouldGetRedisValue() {
        String ian = redissonService.getValue("ian");
        log.info("测试结果:{}", ian);
    }

    // 验证 Redis 键删除
    @Test
    public void shouldRemoveRedisValue() {
        redissonService.remove("60711088280");
    }

}
