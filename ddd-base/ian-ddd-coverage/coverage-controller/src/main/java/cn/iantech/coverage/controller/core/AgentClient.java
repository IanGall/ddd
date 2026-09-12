package cn.iantech.coverage.controller.core;

import org.jacoco.core.data.IExecutionDataVisitor;
import org.jacoco.core.tools.ExecDumpClient;
import org.jacoco.core.tools.ExecFileLoader;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * 与被测 JVM 内 jacocoagent 的 TCP 通信客户端。
 *
 * <p>被测服务以 {@code output=tcpserver} 启动后，Agent 会在指定端口监听。
 * 采集与清零统一走 JaCoCo 官方的 {@link ExecDumpClient} dump 指令，
 * 其 reset 语义即「先 dump 再清零」，因此清零时直接丢弃 dump 结果即可。</p>
 */
public class AgentClient {

    private static final int CONNECT_TIMEOUT_MILLIS = 3_000;

    private static final long RETRY_DELAY_MILLIS = 300L;

    /**
     * 读取 Agent 当前累积的 execution data，不重置探针计数。
     */
    public void dump(CoverageModels.AgentTarget target, IExecutionDataVisitor visitor) {
        collect(target, false).getExecutionDataStore().accept(visitor);
    }

    /**
     * 清零 Agent 的探针计数，既有的类结构信息保留。
     *
     * <p>JaCoCo 不支持独立的 reset 指令，dump(reset=true) 才是 Agent 提供的清零方式。</p>
     */
    public void reset(CoverageModels.AgentTarget target) {
        collect(target, true);
    }

    /**
     * 断开 Agent 与服务端之间的连接，用于 Session 结束后的收尾。
     *
     * <p>Agent 的 tcpserver 会为每个连接维护独立的 RemoteControl 会话，
     * 连接关闭后该会话自行结束，不需要额外的协议指令。</p>
     */
    public void disconnect(CoverageModels.AgentTarget target) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(target.agent().getHost(), target.agent().getPort()),
                    CONNECT_TIMEOUT_MILLIS);
        } catch (IOException e) {
            throw new CoverageException("连接 Agent 失败: " + target.describe() + " -> " + e.getMessage(), e);
        }
    }

    private ExecFileLoader collect(CoverageModels.AgentTarget target, boolean reset) {
        ExecDumpClient client = new ExecDumpClient();
        client.setDump(true);
        client.setReset(reset);
        client.setRetryCount(0);
        client.setRetryDelay(RETRY_DELAY_MILLIS);
        try {
            return client.dump(InetAddress.getByName(target.agent().getHost()), target.agent().getPort());
        } catch (UnknownHostException e) {
            throw new CoverageException("Agent 地址无法解析: " + target.describe(), e);
        } catch (IOException e) {
            throw new CoverageException("dump 失败: " + target.describe() + " -> " + e.getMessage(), e);
        }
    }
}
