package cn.iantech.test;

import cn.iantech.IanFrameArchetypeStdApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiTest {

    // 验证标准工程声明了 Spring Boot 启动入口
    @Test
    void shouldDeclareSpringBootEntryPoint() {
        assertTrue(IanFrameArchetypeStdApplication.class.isAnnotationPresent(SpringBootApplication.class));
    }

}
