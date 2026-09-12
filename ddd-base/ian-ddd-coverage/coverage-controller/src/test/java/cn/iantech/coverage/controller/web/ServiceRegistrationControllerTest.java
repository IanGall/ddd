package cn.iantech.coverage.controller.web;

import cn.iantech.coverage.controller.WorkDirectory;
import cn.iantech.coverage.controller.config.CoverageProperties;
import cn.iantech.coverage.controller.core.AgentRegistry;
import cn.iantech.coverage.controller.core.DynamicServiceRegistrar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 服务注册接口的 HTTP 契约：成功 200、参数非法 400、冲突 409、注销 204。
 */
class ServiceRegistrationControllerTest {

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;

    private Path classesDirectory;

    @BeforeEach
    void setUp() throws Exception {
        WorkDirectory.override(tempDir);
        classesDirectory = Files.createDirectories(tempDir.resolve("order-classes"));

        CoverageProperties properties = new CoverageProperties();
        CoverageProperties.Agent configured = new CoverageProperties.Agent();
        configured.setName("gateway");
        configured.setPort(6300);
        CoverageProperties.ServiceReport report = new CoverageProperties.ServiceReport();
        report.setName("gateway");
        report.setClassesDirectories(List.of(classesDirectory.toString()));
        properties.setAgents(List.of(configured));
        properties.setReports(List.of(report));

        DynamicServiceRegistrar registrar =
                new DynamicServiceRegistrar(new AgentRegistry(properties), tempDir);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ServiceRegistrationController(registrar))
                .setControllerAdvice(new CoverageExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        WorkDirectory.reset();
    }

    @Test
    void shouldRegisterListAndUnregister() throws Exception {
        mockMvc.perform(post("/api/coverage/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("order", 6302, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("order"))
                .andExpect(jsonPath("$.runtimeRegistered").value(true))
                .andExpect(jsonPath("$.agentPort").value(6302));

        mockMvc.perform(get("/api/coverage/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("gateway"))
                .andExpect(jsonPath("$[0].runtimeRegistered").value(false))
                .andExpect(jsonPath("$[1].name").value("order"));

        mockMvc.perform(delete("/api/coverage/services/{name}", "order"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/coverage/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("gateway"));
    }

    @Test
    void shouldReturnBadRequestForInvalidInput() throws Exception {
        mockMvc.perform(post("/api/coverage/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("../escape", 6302, false)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/coverage/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("bad-port", 70000, false)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("agentPort")));
    }

    @Test
    void shouldReturnConflictForDuplicateAndConfiguredService() throws Exception {
        mockMvc.perform(post("/api/coverage/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("order", 6302, false)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/coverage/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("order", 6303, false)))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("replace=true")));

        // replace=true 覆盖同名的运行时注册
        mockMvc.perform(post("/api/coverage/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("order", 6303, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentPort").value(6303));

        // 配置来源的服务同名注册且未声明 replace，同样冲突
        mockMvc.perform(post("/api/coverage/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("gateway", 6301, false)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnNotFoundWhenUnregisteringUnknownService() throws Exception {
        mockMvc.perform(delete("/api/coverage/services/{name}", "not-registered"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectUnregisteringConfiguredService() throws Exception {
        mockMvc.perform(delete("/api/coverage/services/{name}", "gateway"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("配置文件")));
    }

    private String body(String name, int port, boolean replace) {
        return """
                {"name":"%s","host":"127.0.0.1","agentPort":%d,
                 "classesDirectories":["%s"],"sourceDirectories":[],"replace":%s}
                """.formatted(name, port, classesDirectory.toString().replace("\\", "\\\\"), replace);
    }
}
