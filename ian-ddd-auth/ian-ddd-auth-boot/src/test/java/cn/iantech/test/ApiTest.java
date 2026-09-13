package cn.iantech.test;

import cn.iantech.IanDddAuthApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiTest {

    // 验证标准工程声明了 Spring Boot 启动入口
    @Test
    void shouldDeclareSpringBootEntryPoint() {
        assertTrue(IanDddAuthApplication.class.isAnnotationPresent(SpringBootApplication.class));
    }

}
