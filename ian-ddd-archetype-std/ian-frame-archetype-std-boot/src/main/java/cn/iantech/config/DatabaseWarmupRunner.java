package cn.iantech.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseWarmupRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        long startTime = System.currentTimeMillis();
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            long cost = System.currentTimeMillis() - startTime;
            log.info("数据库连接预热完成，结果={}, 耗时={}ms", result, cost);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - startTime;
            log.warn("数据库连接预热失败，将在首次业务请求时重试，耗时={}ms", cost, e);
        }
    }

}
