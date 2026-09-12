package cn.iantech.coverage.controller.config;

import cn.iantech.coverage.controller.core.*;
import cn.iantech.coverage.controller.report.ConsistencyCheckService;
import cn.iantech.coverage.controller.report.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 覆盖率控制器装配。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CoverageProperties.class)
public class CoverageConfig {

    private static final Logger log = LoggerFactory.getLogger(CoverageConfig.class);

    @Bean
    public AgentRegistry agentRegistry(CoverageProperties properties) {
        AgentRegistry registry = new AgentRegistry(properties);
        log.info("覆盖率 Agent 注册完成: {}", registry.allAgents().stream()
                .map(agent -> agent.getName() + "@" + agent.getHost() + ":" + agent.getPort())
                .toList());
        log.info("报告服务注册完成: {}", registry.allReports().stream()
                .map(CoverageProperties.ServiceReport::getName)
                .toList());
        return registry;
    }

    @Bean
    public AgentClient agentClient() {
        return new AgentClient();
    }

    @Bean
    public DynamicServiceRegistrar dynamicServiceRegistrar(AgentRegistry registry, CoverageProperties properties) {
        Path workDirectory = PathResolver.resolve(properties.getSession().getWorkDirectory());
        DynamicServiceRegistrar registrar = new DynamicServiceRegistrar(registry, workDirectory);
        registrar.restore();
        return registrar;
    }

    @Bean
    public ExecMergeService execMergeService() {
        return new ExecMergeService();
    }

    @Bean
    public ConsistencyCheckService consistencyCheckService(AgentRegistry registry) {
        return new ConsistencyCheckService(registry);
    }

    @Bean
    public ReportService reportService(AgentRegistry registry, ConsistencyCheckService consistencyCheckService) {
        return new ReportService(registry, consistencyCheckService);
    }

    @Bean
    public SessionService sessionService(AgentRegistry registry, AgentClient agentClient,
                                         ExecMergeService mergeService, ReportService reportService,
                                         CoverageProperties properties) {
        Path workDirectory = PathResolver.resolve(properties.getSession().getWorkDirectory());
        log.info("覆盖率工作目录: {}", workDirectory);
        validateReportSources(registry);
        return new SessionService(registry, agentClient, mergeService, reportService, workDirectory);
    }

    /**
     * 启动时校验报告所需的 classes 目录，尽早暴露类路径配置问题。
     *
     * <p>只告警不中断：允许先启动控制器，再去构建或启动被测服务。若一个可用目录都没有，
     * 生成报告时会明确失败。</p>
     */
    private void validateReportSources(AgentRegistry registry) {
        List<String> missing = new ArrayList<>();
        int available = 0;
        for (CoverageProperties.ServiceReport service : registry.allReports()) {
            for (String configured : service.getClassesDirectories()) {
                Path path = PathResolver.resolve(configured);
                if (Files.isDirectory(path)) {
                    available++;
                } else {
                    missing.add(service.getName() + " -> " + path);
                }
            }
        }
        if (!missing.isEmpty()) {
            log.warn("以下报告 classes 目录不存在，对应服务若参与采集请先构建，否则其报告将缺失: {}", missing);
        }
        if (available == 0) {
            log.warn("当前没有任何可用的 classes 目录，finish 时将无法生成报告，请检查 coverage.reports 配置");
        }
    }
}
