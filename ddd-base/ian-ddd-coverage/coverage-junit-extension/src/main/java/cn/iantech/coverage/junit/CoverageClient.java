package cn.iantech.coverage.junit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 覆盖率控制器的 HTTP 客户端。
 */
final class CoverageClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final String baseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    CoverageClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * 便于日志输出 Agent 列表。
     */
    static String describe(List<String> agentNames) {
        return agentNames == null || agentNames.isEmpty() ? "全部 Agent" : Arrays.toString(agentNames.toArray());
    }

    /**
     * 控制器是否可用。
     */
    boolean isAvailable() {
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/api/coverage/sessions"))
                            .timeout(CONNECT_TIMEOUT)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            // 未创建 Session 时控制器返回 404，能响应即视为可用
            return response.statusCode() < 500;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    SessionResponse startSession(String name, String buildId) {
        String body = json(new StartSessionRequest(name, buildId));
        return expect(send(post("/api/coverage/sessions", body)), 200, SessionResponse.class);
    }

    DumpResponse reset(String sessionId, List<String> agentNames) {
        return postCommand(sessionId, "reset", agentNames);
    }

    DumpResponse dump(String sessionId, List<String> agentNames) {
        return postCommand(sessionId, "dump", agentNames);
    }

    ReportResponse finish(String sessionId) {
        return expect(send(post("/api/coverage/sessions/" + sessionId + "/finish", null)), 200,
                ReportResponse.class);
    }

    private DumpResponse postCommand(String sessionId, String action, List<String> agentNames) {
        String body = json(new AgentSelectionRequest(agentNames));
        return expect(send(post("/api/coverage/sessions/" + sessionId + "/" + action, body)), 200,
                DumpResponse.class);
    }

    private HttpRequest post(String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json");
        if (body == null) {
            return builder.POST(HttpRequest.BodyPublishers.noBody()).build();
        }
        return builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private <T> T expect(HttpResponse<String> response, int expectedStatus, Class<T> type) {
        if (response.statusCode() != expectedStatus) {
            throw new CoverageClientException("控制器返回 " + response.statusCode() + ": " + response.body());
        }
        try {
            return MAPPER.readValue(response.body(), type);
        } catch (IOException e) {
            throw new CoverageClientException("解析控制器响应失败: " + response.body(), e);
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new CoverageClientException("请求覆盖率控制器失败: " + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CoverageClientException("等待覆盖率控制器响应被中断: " + request.uri(), e);
        }
    }

    private String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new CoverageClientException("序列化请求失败", e);
        }
    }

    /**
     * 客户端调用异常。
     */
    static class CoverageClientException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        CoverageClientException(String message) {
            super(message);
        }

        CoverageClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private record StartSessionRequest(String name, String buildId) {
    }

    private record AgentSelectionRequest(List<String> agentNames) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SessionResponse(String id, String name, String buildId, String status, String directory,
                           String reportUrl, List<AgentAvailability> agents) {

        /**
         * 不可达的 Agent 名称列表。
         */
        List<String> unreachableAgents() {
            if (agents == null) {
                return List.of();
            }
            return agents.stream()
                    .filter(agent -> !agent.reachable())
                    .map(AgentAvailability::agentName)
                    .toList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AgentAvailability(String agentName, boolean reachable, String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DumpResponse(String sessionId, int successCount, int failedCount, List<AgentDumpResult> results) {

        /**
         * 失败的 Agent 描述，用于在测试日志里提示。
         */
        List<String> failedAgents() {
            if (results == null) {
                return List.of();
            }
            return results.stream()
                    .filter(result -> !result.success())
                    .map(AgentDumpResult::agentName)
                    .toList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AgentDumpResult(String agentName, boolean success, String execFile, String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReportResponse(String dashboardFile, String summaryFile, String overallReportUrl,
                          List<ServiceCoverage> services, ServiceCoverage overall,
                          Map<String, ConsistencyReport> consistency) {

        String describe() {
            if (overall == null) {
                return "未生成覆盖率汇总";
            }
            StringBuilder text = new StringBuilder();
            if (services != null) {
                for (ServiceCoverage service : services) {
                    text.append("    ").append(service.name())
                            .append(": 行覆盖 ").append(service.lineRatio()).append("%")
                            .append("（已覆盖 ").append(service.lineCovered())
                            .append("/").append(service.lineTotal()).append("）\n");
                }
            }
            text.append("    overall: 行覆盖 ").append(overall.lineRatio()).append("%")
                    .append("（已覆盖 ").append(overall.lineCovered())
                    .append("/").append(overall.lineTotal()).append("）");
            return text.toString();
        }

        /**
         * classId 失配提示；没有失配时返回空字符串。
         */
        String describeConsistencyIssue() {
            if (consistency == null || consistency.isEmpty()) {
                return "";
            }
            StringBuilder text = new StringBuilder();
            consistency.forEach((agent, report) -> {
                if (report != null && report.mismatchedCount() > 0) {
                    text.append("  Agent [").append(agent).append("] 有 ")
                            .append(report.mismatchedCount()).append(" 个类的字节码与报告目录不一致，")
                            .append("这些类的覆盖率被误报为 0%：\n");
                    if (report.mismatches() != null) {
                        report.mismatches().forEach(mismatch -> text.append("    ")
                                .append(mismatch.className())
                                .append("（被测进程 ").append(mismatch.execClassId())
                                .append(" / 报告目录 ").append(mismatch.localClassId()).append("）\n"));
                    }
                }
            });
            if (text.isEmpty()) {
                return "";
            }
            text.append("  原因：被测服务启动后源码被重新编译，或服务与报告使用了不同次构建的产物。\n")
                    .append("  处理：清理并重新构建后重启被测服务，再执行测试。");
            return text.toString();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ConsistencyReport(int alignedCount, int mismatchedCount, int absentCount,
                             List<ClassMismatch> mismatches) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ClassMismatch(String className, String execClassId, String localClassId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ServiceCoverage(String name, int classCount, int lineTotal, int lineCovered, int lineMissed,
                           int branchTotal, int branchCovered) {

        /**
         * 行覆盖率，0~100，保留两位小数。
         */
        double lineRatio() {
            if (lineTotal <= 0) {
                return 0.0;
            }
            return Math.round(lineCovered * 10000.0 / lineTotal) / 100.0;
        }
    }
}
