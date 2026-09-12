package cn.iantech.coverage.controller;

import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfo;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.*;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本机假 JaCoCo Agent：以 tcpserver 模式监听，并复刻真实 jacocoagent 的响应格式。
 *
 * <p>真实 Agent 的 dump 响应结构为「file header + session info + execution data + cmd-ok」，
 * 与 {@link org.jacoco.core.tools.ExecDumpClient} 的读取方式完全对齐。</p>
 */
final class FakeJacocoAgent implements AutoCloseable {

    private final ServerSocket serverSocket;

    private final IRuntime runtime;

    private final RuntimeData runtimeData;

    /**
     * 用于定义插桩后类的子加载器：JaCoCo 分析按类名匹配，因此插桩类可以独立加载。
     */
    private final InstrumentedClassLoader classLoader = new InstrumentedClassLoader();

    private final AtomicInteger activeConnections = new AtomicInteger();

    private final AtomicInteger servedConnections = new AtomicInteger();

    private FakeJacocoAgent(ServerSocket serverSocket, IRuntime runtime, RuntimeData runtimeData) {
        this.serverSocket = serverSocket;
        this.runtime = runtime;
        this.runtimeData = runtimeData;
    }

    static FakeJacocoAgent start(int port) throws Exception {
        ServerSocket serverSocket = new ServerSocket(port, 1, InetAddress.getLoopbackAddress());
        SystemPropertiesRuntime runtime = new SystemPropertiesRuntime();
        RuntimeData runtimeData = new RuntimeData();
        runtime.startup(runtimeData);
        FakeJacocoAgent agent = new FakeJacocoAgent(serverSocket, runtime, runtimeData);
        Thread acceptor = new Thread(agent::acceptLoop, "fake-jacoco-agent-" + port);
        acceptor.setDaemon(true);
        acceptor.start();
        return agent;
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket connection = serverSocket.accept();
                Thread worker = new Thread(() -> serve(connection), "fake-jacoco-connection");
                worker.setDaemon(true);
                worker.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    private void serve(Socket connection) {
        activeConnections.incrementAndGet();
        try (Socket socket = connection) {
            // 客户端会先写入 file header，RemoteControlReader 构造时会消费掉
            RemoteControlReader reader = new RemoteControlReader(
                    new BufferedInputStream(socket.getInputStream()));
            OutputStream output = new BufferedOutputStream(socket.getOutputStream());
            reader.setRemoteCommandVisitor(new CommandVisitor(output));
            while (reader.read()) {
                // 持续处理客户端指令，直到客户端关闭连接
            }
        } catch (IOException e) {
            // 连接被客户端关闭属于正常情况
        } finally {
            activeConnections.decrementAndGet();
            servedConnections.incrementAndGet();
        }
    }

    /**
     * 加载并插桩 {@link TargetBundle}，为后续产生探针数据做准备。
     *
     * @param javaAgentDestFile {@code SystemPropertiesRuntime} 需要的 destfile 占位路径
     */
    void prepareInstrumentedBundle(Path javaAgentDestFile) throws Exception {
        System.setProperty("jacoco-agent.destfile", javaAgentDestFile.toString());
        String resource = "/" + TargetBundle.class.getName().replace('.', '/') + ".class";
        byte[] instrumented;
        try (InputStream input = TargetBundle.class.getResourceAsStream(resource)) {
            instrumented = new Instrumenter(runtime).instrument(input, TargetBundle.class.getName());
        }
        classLoader.define(TargetBundle.class.getName(), instrumented);
    }

    /**
     * 在插桩后的类上执行被覆盖的方法，产生真实的探针命中数据。
     */
    void cover() throws Exception {
        Class<?> instrumentedClass = Class.forName(TargetBundle.class.getName(), true, classLoader);
        Object instance = instrumentedClass.getDeclaredConstructor().newInstance();
        instrumentedClass.getMethod("covered").invoke(instance);
    }

    int servedConnectionCount() {
        return servedConnections.get();
    }

    int activeConnectionCount() {
        return activeConnections.get();
    }

    @Override
    public void close() throws Exception {
        try {
            serverSocket.close();
        } catch (IOException e) {
            // 关闭失败可忽略
        }
        runtime.shutdown();
        String destFile = System.getProperty("jacoco-agent.destfile");
        if (destFile != null) {
            Files.deleteIfExists(Path.of(destFile));
            System.clearProperty("jacoco-agent.destfile");
        }
    }

    /**
     * 覆盖探针数据的目标类：第一个方法被覆盖，第二个方法不覆盖。
     */
    public static class TargetBundle {

        public boolean covered() {
            return true;
        }

        public boolean uncovered() {
            return false;
        }
    }

    /**
     * 子优先类加载器：对 {@link TargetBundle} 及其内部类使用插桩后的字节码，其余委托父加载器。
     */
    private static final class InstrumentedClassLoader extends ClassLoader {

        private final Map<String, byte[]> definitions = new ConcurrentHashMap<>();

        private InstrumentedClassLoader() {
            super(FakeJacocoAgent.class.getClassLoader());
        }

        private void define(String className, byte[] bytes) {
            definitions.put(className, bytes);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(TargetBundle.class.getName())) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        byte[] bytes = definitions.get(name);
                        if (bytes == null) {
                            // 内部类等未插桩的类型，直接读取原始字节
                            try (InputStream input = TargetBundle.class.getResourceAsStream(
                                    "/" + name.replace('.', '/') + ".class")) {
                                bytes = input == null ? null : input.readAllBytes();
                            } catch (IOException e) {
                                throw new ClassNotFoundException(name, e);
                            }
                        }
                        if (bytes == null) {
                            throw new ClassNotFoundException(name);
                        }
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
            return super.loadClass(name, resolve);
        }
    }

    /**
     * 与真实 Agent 一致的指令处理：dump 时写数据与结束标记，reset 时仅清零探针。
     */
    private final class CommandVisitor implements IRemoteCommandVisitor {

        private final OutputStream output;

        private CommandVisitor(OutputStream output) {
            this.output = output;
        }

        @Override
        public void visitDumpCommand(boolean dump, boolean reset) throws IOException {
            if (dump) {
                RemoteControlWriter writer = new RemoteControlWriter(output);
                writer.visitSessionInfo(new SessionInfo("fake-agent", System.currentTimeMillis(),
                        System.currentTimeMillis()));
                ExecutionDataStore store = new ExecutionDataStore();
                runtimeData.collect(store, sessionInfo -> {
                }, reset);
                for (ExecutionData data : store.getContents()) {
                    writer.visitClassExecution(data);
                }
                writer.sendCmdOk();
            } else if (reset) {
                runtimeData.reset();
            }
            output.flush();
        }
    }
}
