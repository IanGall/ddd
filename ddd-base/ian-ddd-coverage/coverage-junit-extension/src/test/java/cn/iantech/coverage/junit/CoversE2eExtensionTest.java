package cn.iantech.coverage.junit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用 JDK 内置 HttpServer 模拟覆盖率控制器，验证扩展的生命周期调用顺序。
 */
class CoversE2eExtensionTest {

    private static final int PORT = 18099;

    private static final List<String> CALLS = new CopyOnWriteArrayList<>();

    private static HttpServer server;

    @BeforeAll
    static void startFakeController() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        server.createContext("/api/coverage/sessions", CoversE2eExtensionTest::handle);
        server.setExecutor(null);
        server.start();
    }

    @AfterAll
    static void stopFakeController() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (path.endsWith("/finish")) {
            CALLS.add("finish");
            respond(exchange, """
                    {"dashboardFile":"/tmp/dashboard.html","summaryFile":"/tmp/overall.xml",
                     "overallReportUrl":"/api/coverage/sessions/s1/reports/index.html",
                     "services":[{"name":"gateway","classCount":3,"lineTotal":100,"lineCovered":80,
                                  "lineMissed":20,"branchTotal":10,"branchCovered":5,"lineRatio":80.0}],
                     "overall":{"name":"overall","classCount":3,"lineTotal":100,"lineCovered":73,
                                "lineMissed":27,"branchTotal":10,"branchCovered":5,"lineRatio":73.0}}""");
            return;
        }
        if (path.endsWith("/reset")) {
            CALLS.add("reset");
            respond(exchange, dumpOk());
            return;
        }
        if (path.endsWith("/dump")) {
            CALLS.add("dump");
            respond(exchange, dumpOk());
            return;
        }
        if ("POST".equals(exchange.getRequestMethod())) {
            CALLS.add("start");
            respond(exchange, """
                    {"id":"s1","name":"test","buildId":"b1","status":"RUNNING",
                     "directory":"file:///tmp/sessions/s1","reportUrl":"/api/coverage/sessions/s1/reports/index.html",
                     "agents":[{"agentName":"gateway","reachable":true,"message":"ok"}]}""");
            return;
        }
        respond(exchange, "{}");
    }

    private static String dumpOk() {
        return """
                {"sessionId":"s1","successCount":1,"failedCount":0,
                 "results":[{"agentName":"gateway","success":true,"execFile":"/tmp/gateway.exec","message":"ok"}]}""";
    }

    private static void respond(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @Test
    void shouldRunFullCoverageLifecycleAroundAnnotatedTest() {
        CALLS.clear();

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(RecordingE2eCase.class))
                .build();
        Launcher launcher = LauncherFactory.create();
        launcher.execute(request);

        assertEquals(List.of("start", "reset", "dump", "finish"), CALLS,
                "扩展应按 start → reset → dump → finish 的顺序调用控制器");
        assertTrue(CALLS.contains("dump"), "每次测试后都应执行 dump");
    }

    /**
     * 被扩展包裹的真实测试用例。
     */
    @CoversE2e(value = "extension-test", buildId = "test-build", agents = {"gateway"})
    static class RecordingE2eCase {

        @Test
        void shouldPass() {
            List<String> snapshot = new ArrayList<>(CALLS);
            assertTrue(snapshot.contains("start"), "测试执行前应已创建 Session");
            assertTrue(snapshot.contains("reset"), "测试执行前应已 reset Agent");
        }
    }
}
