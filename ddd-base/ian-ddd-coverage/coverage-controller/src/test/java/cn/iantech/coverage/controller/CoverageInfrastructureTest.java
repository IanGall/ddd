package cn.iantech.coverage.controller;

import cn.iantech.coverage.controller.config.CoverageProperties;
import cn.iantech.coverage.controller.core.AgentClient;
import cn.iantech.coverage.controller.core.CoverageModels;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证假 Agent 与真实 JaCoCo Agent 的响应协议一致，且 dump / reset / disconnect 语义正确。
 */
class CoverageInfrastructureTest {

    private static final int PORT = 6399;

    private static FakeJacocoAgent agent;

    @BeforeAll
    static void startAgent(@TempDir Path tempDir) throws Exception {
        agent = FakeJacocoAgent.start(PORT);
        agent.prepareInstrumentedBundle(tempDir.resolve("jacoco-fake.exec"));
        agent.cover();
    }

    @AfterAll
    static void stopAgent() throws Exception {
        if (agent != null) {
            agent.close();
        }
    }

    private static String className() {
        return FakeJacocoAgent.TargetBundle.class.getName().replace('.', '/');
    }

    @Test
    void shouldCollectAndResetProbeDataOverRemoteProtocol() {
        AgentClient client = new AgentClient();
        CoverageModels.AgentTarget target = target();
        ExecutionDataStore store = new ExecutionDataStore();

        client.dump(target, store);
        assertTrue(store.getContents().stream().anyMatch(
                        data -> data.getName().equals(className()) && data.hasHits()),
                "dump 应返回已命中的探针数据");

        client.reset(target);

        ExecutionDataStore afterReset = new ExecutionDataStore();
        client.dump(target, afterReset);
        assertTrue(afterReset.getContents().stream().noneMatch(ExecutionData::hasHits),
                "reset 之后不应再有任何命中");

        client.disconnect(target);
        assertTrue(agent.servedConnectionCount() >= 3, "每次指令都应建立过独立连接");
    }

    private CoverageModels.AgentTarget target() {
        CoverageProperties.Agent agentConfig = new CoverageProperties.Agent();
        agentConfig.setName("fake");
        agentConfig.setHost("127.0.0.1");
        agentConfig.setPort(PORT);
        return new CoverageModels.AgentTarget(agentConfig);
    }
}
