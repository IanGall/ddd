package cn.iantech.coverage.controller;

import cn.iantech.coverage.controller.config.CoverageProperties;
import cn.iantech.coverage.controller.core.AgentClient;
import cn.iantech.coverage.controller.core.AgentRegistry;
import cn.iantech.coverage.controller.core.ExecMergeService;
import cn.iantech.coverage.controller.core.SessionService;
import cn.iantech.coverage.controller.report.ConsistencyCheckService;
import cn.iantech.coverage.controller.report.ReportOutcome;
import cn.iantech.coverage.controller.report.ReportService;
import cn.iantech.coverage.controller.web.CoverageController;
import cn.iantech.coverage.controller.web.CoverageExceptionHandler;
import cn.iantech.coverage.controller.web.ReportResourceController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证并集报告：一轮测试按测试类拆成多个 Session 时，
 * 必须能把它们的 execution data 合并成一份代表整轮的覆盖率报告。
 */
class CoverageMergeReportTest {

    private static final int REACHABLE_PORT = 6397;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static FakeJacocoAgent agent;

    private static SessionService sessionService;

    private static MockMvc mockMvc;

    @BeforeAll
    static void setUp(@TempDir Path tempDir) throws Exception {
        WorkDirectory.override(tempDir);

        CoverageProperties properties = new CoverageProperties();
        properties.getSession().setWorkDirectory(tempDir.toString());
        properties.setAgents(List.of(agent()));
        properties.setReports(List.of(report()));

        AgentRegistry registry = new AgentRegistry(properties);
        sessionService = new SessionService(registry, new AgentClient(), new ExecMergeService(),
                new ReportService(registry, new ConsistencyCheckService(registry)), tempDir);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CoverageController(sessionService), new ReportResourceController(sessionService))
                .setControllerAdvice(new CoverageExceptionHandler())
                .build();

        agent = FakeJacocoAgent.start(REACHABLE_PORT);
        agent.prepareInstrumentedBundle(tempDir.resolve("jacoco-fake.exec"));
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (agent != null) {
            agent.close();
        }
    }

    private static CoverageProperties.Agent agent() {
        CoverageProperties.Agent agent = new CoverageProperties.Agent();
        agent.setName("std");
        agent.setHost("127.0.0.1");
        agent.setPort(REACHABLE_PORT);
        return agent;
    }

    private static CoverageProperties.ServiceReport report() {
        CoverageProperties.ServiceReport report = new CoverageProperties.ServiceReport();
        report.setName("std");
        report.setClassesDirectories(List.of(Path.of("target/test-classes").toAbsolutePath().toString()));
        report.setSourceDirectories(List.of(Path.of("src/test/java").toAbsolutePath().toString()));
        return report;
    }

    @Test
    void shouldMergeFinishedSessionsIntoUnionReport() throws Exception {
        String firstSession = runAndFinishSession("first-class");
        String secondSession = runAndFinishSession("second-class");

        MvcResult result = mockMvc.perform(post("/api/coverage/reports/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"e2e-run\",\"sessionIds\":[\"" + firstSession + "\",\""
                                + secondSession + "\"]}"))
                .andExpect(status().isOk())
                .andReturn();

        ReportOutcome outcome = MAPPER.readValue(result.getResponse().getContentAsString(), ReportOutcome.class);
        assertTrue(outcome.overall().lineTotal() > 0, "并集报告应包含可分析的行");
        assertTrue(outcome.overall().lineCovered() > 0, "并集报告应包含已覆盖行");
        assertEquals(1, outcome.services().size(), "注册服务应出现在并集报告中");
        assertTrue(Files.isRegularFile(Path.of(outcome.dashboardFile())), "并集报告应产出 dashboard.html");
    }

    @Test
    void shouldRejectMergeWithUnknownOrUnfinishedSession() throws Exception {
        // 不存在的 Session 与既有会话接口保持一致：按参数不合法返回 400
        mockMvc.perform(post("/api/coverage/reports/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionIds\":[\"not-exists\"]}"))
                .andExpect(status().isBadRequest());

        // 新建但未 finish 的 Session 不可参与合并
        MvcResult created = mockMvc.perform(post("/api/coverage/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"unfinished\"}"))
                .andExpect(status().isOk()).andReturn();
        String unfinishedId = MAPPER.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/coverage/reports/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionIds\":[\"" + unfinishedId + "\"]}"))
                .andExpect(status().is5xxServerError());

        // 合并失败不改变 Session 状态：该 Session 仍处于 RUNNING，结束后可继续新建
        mockMvc.perform(post("/api/coverage/sessions")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/coverage/sessions/{id}/finish", unfinishedId))
                .andExpect(status().is5xxServerError());
        mockMvc.perform(post("/api/coverage/sessions")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    private String runAndFinishSession(String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/coverage/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk()).andReturn();
        String id = MAPPER.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/coverage/sessions/{id}/reset", id)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        Path execFile = sessionService.workDirectory().resolve("sessions").resolve(id).resolve("std.exec");
        long deadline = System.currentTimeMillis() + 5_000L;
        JsonNode dump = null;
        while (System.currentTimeMillis() < deadline) {
            agent.cover();
            MvcResult dumped = mockMvc.perform(post("/api/coverage/sessions/{id}/dump", id)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk()).andReturn();
            dump = MAPPER.readTree(dumped.getResponse().getContentAsString());
            if (Files.isRegularFile(execFile) && dump.get("successCount").asInt() == 1) {
                break;
            }
            Thread.sleep(100L);
        }
        assertTrue(dump != null && dump.get("successCount").asInt() == 1, "dump 未在预期时间内产出 exec 数据");

        mockMvc.perform(post("/api/coverage/sessions/{id}/finish", id)).andExpect(status().isOk());
        return id;
    }
}
