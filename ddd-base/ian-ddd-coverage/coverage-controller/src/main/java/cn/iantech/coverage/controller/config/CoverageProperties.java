package cn.iantech.coverage.controller.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 分布式覆盖率控制器配置：注册参与覆盖率采集的 JVM Agent，以及生成报告所需的类与源码位置。
 */
@ConfigurationProperties(prefix = "coverage")
public class CoverageProperties {

    @NestedConfigurationProperty
    private Session session = new Session();

    @NestedConfigurationProperty
    private List<Agent> agents = new ArrayList<>();

    @NestedConfigurationProperty
    private List<ServiceReport> reports = new ArrayList<>();

    public Session getSession() {
        return session;
    }

    public void setSession(Session value) {
        this.session = value;
    }

    public List<Agent> getAgents() {
        return agents;
    }

    public void setAgents(List<Agent> value) {
        this.agents = value;
    }

    public List<ServiceReport> getReports() {
        return reports;
    }

    public void setReports(List<ServiceReport> value) {
        this.reports = value;
    }

    /**
     * 控制器自身运行参数。
     */
    public static class Session {

        /**
         * 覆盖率产物根目录，所有 Session 的 exec 与报告都落在此目录下。
         */
        private String workDirectory = "coverage";

        public String getWorkDirectory() {
            return workDirectory;
        }

        public void setWorkDirectory(String value) {
            this.workDirectory = value;
        }
    }

    /**
     * 一个参与采集的 JVM Agent，对应被测服务启动时注入的 jacocoagent。
     */
    public static class Agent {

        /**
         * Agent 唯一标识，需要与被测服务启动参数中的 sessionid 保持一致。
         */
        private String name;

        private String host = "127.0.0.1";

        /**
         * jacocoagent 以 tcpserver 模式暴露的端口。
         */
        private int port = 6300;

        /**
         * Agent 未就绪时是否允许跳过：false 时 dump 失败只会记录失败，不会中断流程。
         */
        private boolean required = true;

        public String getName() {
            return name;
        }

        public void setName(String value) {
            this.name = value;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String value) {
            this.host = value;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int value) {
            this.port = value;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean value) {
            this.required = value;
        }
    }

    /**
     * 报告中一个被测服务的类与源码位置，用于把 exec 数据落到具体代码上。
     */
    public static class ServiceReport {

        /**
         * 报告名称，优先与 Agent 的 name 对应。
         */
        private String name;

        /**
         * 编译产物目录列表，默认 target/classes 相对于控制器工作目录。
         */
        private List<String> classesDirectories = new ArrayList<>();

        /**
         * 源码目录列表，用于在报告里展示行级覆盖明细。
         */
        private List<String> sourceDirectories = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String value) {
            this.name = value;
        }

        public List<String> getClassesDirectories() {
            return classesDirectories;
        }

        public void setClassesDirectories(List<String> value) {
            this.classesDirectories = value;
        }

        public List<String> getSourceDirectories() {
            return sourceDirectories;
        }

        public void setSourceDirectories(List<String> value) {
            this.sourceDirectories = value;
        }
    }
}
