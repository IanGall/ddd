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
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.tools.ExecFileLoader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 不启动真实被测服务，用本机假 Agent 验证 Session 全流程：
 * start → reset → dump（含失败降级）→ finish（merge + 报告 + dashboard）。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CoverageSessionFlowTest {

    private static final int REACHABLE_PORT = 6399;

    private static final int UNREACHABLE_PORT = 6398;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static FakeJacocoAgent agent;

    private static SessionService sessionService;

    private static MockMvc mockMvc;

    private static String sessionId;

    private static Path sessionDirectory;

    @BeforeAll
    static void setUp(@TempDir Path tempDir) throws Exception {
        WorkDirectory.override(tempDir);

        CoverageProperties properties = new CoverageProperties();
        properties.getSession().setWorkDirectory(tempDir.toString());
        properties.setAgents(List.of(agent("reachable", REACHABLE_PORT), agent("unreachable", UNREACHABLE_PORT)));
        properties.setReports(List.of(report("reachable"), report("unreachable")));

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

    private static CoverageProperties.Agent agent(String name, int port) {
        CoverageProperties.Agent agent = new CoverageProperties.Agent();
        agent.setName(name);
        agent.setHost("127.0.0.1");
        agent.setPort(port);
        return agent;
    }

    private static CoverageProperties.ServiceReport report(String name) {
        CoverageProperties.ServiceReport report = new CoverageProperties.ServiceReport();
        report.setName(name);
        report.setClassesDirectories(List.of(Path.of("target/test-classes").toAbsolutePath().toString()));
        report.setSourceDirectories(List.of(Path.of("src/test/java").toAbsolutePath().toString()));
        return report;
    }

    @Test
    @Order(1)
    void shouldStartSessionAndReportAgentAvailability() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/coverage/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CoverageSessionFlowTest\",\"buildId\":\"test-build\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("RUNNING")))
                .andReturn();

        JsonNode session = MAPPER.readTree(result.getResponse().getContentAsString());
        sessionId = session.get("id").asText();
        sessionDirectory = Path.of(java.net.URI.create(session.get("directory").asText()));

        assertTrue(!sessionId.isBlank(), "sessionId 不应为空");
        assertTrue(hasReachableAgent(session), "应至少有一个 Agent 探测为可连接");
        assertTrue(hasUnreachableAgent(session), "未启动的 Agent 应探测为不可连接");
    }

    @Test
    @Order(2)
    void shouldResetAgentsAndDegradeWhenAgentUnavailable() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/coverage/sessions/{id}/reset", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode report = MAPPER.readTree(result.getResponse().getContentAsString());
        assertEquals(1, report.get("successCount").asInt(), "可达 Agent 应 reset 成功");
        assertEquals(1, report.get("failedCount").asInt(), "不可达 Agent 应记录失败而不是整体失败");
    }

    @Test
    @Order(3)
    void shouldDumpExecutionDataAndWriteExecFile() throws Exception {
        JsonNode report = dumpUntilReady();

        assertEquals(1, report.get("successCount").asInt(), "可达 Agent 应 dump 成功");
        assertEquals(1, report.get("failedCount").asInt(), "不可达 Agent 应记录失败");

        Path execFile = sessionDirectory.resolve("reachable.exec");
        assertTrue(Files.isRegularFile(execFile), "应产出 reachable.exec");
        assertTrue(containsHit(execFile, FakeJacocoAgent.TargetBundle.class.getName().replace('.', '/')),
                "exec 中应包含 TargetBundle 的命中数据");
    }

    @Test
    @Order(4)
    void shouldGenerateReportsAndDashboard() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/coverage/sessions/{id}/finish", sessionId))
                .andExpect(status().isOk())
                .andReturn();

        ReportOutcome outcome = MAPPER.readValue(result.getResponse().getContentAsString(), ReportOutcome.class);
        assertTrue(outcome.overall().lineTotal() > 0, "整体报告应包含可分析的行");
        assertTrue(outcome.overall().lineRatio() > 0, "覆盖方法所在行应被统计为已覆盖");
        assertEquals(2, outcome.services().size(), "两个注册服务都应出现在报告汇总中");
        assertTrue(Files.isRegularFile(Path.of(outcome.dashboardFile())), "应产出 dashboard.html");
        // 只有可达 Agent 产出 exec，因此只有它参与 classId 校验
        assertTrue(outcome.consistency().containsKey("reachable"), "可达 Agent 应有 classId 校验结果");
        assertFalse(outcome.consistency().get("reachable").hasMismatch(),
                "同源构建场景下不应出现 classId 失配");

        String dashboard = Files.readString(Path.of(outcome.dashboardFile()));
        assertTrue(dashboard.contains("reachable") && dashboard.contains("unreachable"),
                "dashboard 应展示各服务覆盖率");

        mockMvc.perform(get("/api/coverage/sessions/{id}/reports/index.html", sessionId))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("JaCoCo")));
        mockMvc.perform(get("/api/coverage/sessions/{id}/report", sessionId))
                .andExpect(status().isFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/api/coverage/sessions/" + sessionId + "/reports/index.html"));
        mockMvc.perform(get("/api/coverage/sessions/{id}/reports/dashboard.html", sessionId))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    void shouldReleaseSessionWhenFinishFails() throws Exception {
        // 新建一个没有任何 exec 数据的 Session，finish 会失败
        MvcResult created = mockMvc.perform(post("/api/coverage/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"finish-failure\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String emptySessionId = MAPPER.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/coverage/sessions/{id}/finish", emptySessionId))
                .andExpect(status().is5xxServerError());

        // 失败后必须能创建新 Session，否则后续测试会被永久锁死
        mockMvc.perform(post("/api/coverage/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"after-failure\"}"))
                .andExpect(status().isOk());
    }

    private JsonNode dumpUntilReady() throws Exception {
        JsonNode report = null;
        long deadline = System.currentTimeMillis() + 5_000L;
        Path execFile = sessionDirectory.resolve("reachable.exec");
        while (System.currentTimeMillis() < deadline) {
            // reset 之后才产生命中，保证命中数据属于当前 Session
            agent.cover();
            MvcResult result = mockMvc.perform(post("/api/coverage/sessions/{id}/dump", sessionId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andReturn();
            report = MAPPER.readTree(result.getResponse().getContentAsString());
            if (Files.isRegularFile(execFile) && report.get("successCount").asInt() == 1) {
                return report;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("dump 未在预期时间内产出 exec 数据");
    }

    private boolean hasReachableAgent(JsonNode session) {
        for (JsonNode agent : session.get("agents")) {
            if (agent.get("reachable").asBoolean()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUnreachableAgent(JsonNode session) {
        for (JsonNode agent : session.get("agents")) {
            if (!agent.get("reachable").asBoolean()) {
                return true;
            }
        }
        return false;
    }

    private boolean containsHit(Path execFile, String className) throws IOException {
        ExecFileLoader loader = new ExecFileLoader();
        loader.load(execFile.toFile());
        for (ExecutionData data : loader.getExecutionDataStore().getContents()) {
            if (data.getName().equals(className) && data.hasHits()) {
                return true;
            }
        }
        return false;
    }

}
