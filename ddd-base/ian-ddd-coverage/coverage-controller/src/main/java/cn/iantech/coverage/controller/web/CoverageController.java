package cn.iantech.coverage.controller.web;

import cn.iantech.coverage.controller.core.CoverageModels;
import cn.iantech.coverage.controller.core.SessionService;
import cn.iantech.coverage.controller.report.ReportOutcome;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

/**
 * 覆盖率控制 API：Session 生命周期与报告获取。
 */
@RestController
@RequestMapping("/api/coverage")
public class CoverageController {

    private final SessionService sessionService;

    public CoverageController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/sessions")
    public CoverageModels.SessionView startSession(
            @RequestBody(required = false) CoverageModels.StartSessionRequest request) {
        String name = request == null ? null : request.name();
        String buildId = request == null ? null : request.buildId();
        return sessionService.start(name, buildId);
    }

    @GetMapping("/sessions/{id}")
    public CoverageModels.SessionView getSession(@PathVariable String id) {
        return sessionService.describe(id);
    }

    @PostMapping("/sessions/{id}/reset")
    public CoverageModels.DumpReport reset(@PathVariable String id,
                                           @RequestBody(required = false)
                                           CoverageModels.AgentSelectionRequest request) {
        return sessionService.reset(id, request == null ? null : request.agentNames());
    }

    @PostMapping("/sessions/{id}/dump")
    public CoverageModels.DumpReport dump(@PathVariable String id,
                                          @RequestBody(required = false)
                                          CoverageModels.AgentSelectionRequest request) {
        return sessionService.dump(id, request == null ? null : request.agentNames());
    }

    @PostMapping("/sessions/{id}/finish")
    public ReportOutcome finish(@PathVariable String id) {
        return sessionService.finish(id);
    }

    /**
     * 合并多个已完成 Session，生成并集报告。
     *
     * <p>一轮测试按测试类产生多个 Session 时，用本接口得到整轮的真实覆盖率。</p>
     */
    @PostMapping("/reports/merge")
    public ReportOutcome mergeReports(@RequestBody CoverageModels.MergeSessionsRequest request) {
        return sessionService.merge(request.name(), request.sessionIds());
    }

    /**
     * 跳转到整体 HTML 报告首页。
     */
    @GetMapping("/sessions/{id}/report")
    public ResponseEntity<Void> report(@PathVariable String id) {
        requireReport(id);
        URI location = URI.create("/api/coverage/sessions/" + id + "/reports/index.html");
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    private void requireReport(String id) {
        sessionService.findReport(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session 尚未生成报告: " + id));
    }
}
