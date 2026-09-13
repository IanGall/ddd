# 接入分布式 E2E 覆盖率

本文说明如何把 **从骨架扩展出来的新服务或新网关**接入分布式覆盖率采集。

适用对象：

- 用 `scaffold-std` 生成的 DDD 认证服务（api / domain / infrastructure / trigger / boot 五模块）
- 用 `scaffold-gateway` 生成的网关应用
- 任何以 Spring Boot 启动、需要被 E2E 测试覆盖的服务

核心思路： **测试只写在被测入口（通常是网关）里，覆盖率由每个服务各自的 JVM 独立采集**，
控制器只负责下发 dump / reset 指令、合并 exec、生成报告。服务之间不需要传递覆盖率数据。

```text
        E2E 测试（只调用网关 HTTP 接口）
                 │
                 ▼
        ┌──────────────────┐  Agent :6300
        │ 网关 / 被测入口   │
        └────────┬─────────┘
                 │ Dubbo
                 ▼
        ┌──────────────────┐  Agent :6301
        │  新服务 A         │
        └────────┬─────────┘
                 │ Dubbo
                 ▼
        ┌──────────────────┐  Agent :6302
        │  新服务 B         │
        └──────────────────┘

   coverage-controller 依次连接各 Agent → dump → merge → 报告
```

---

## 一、接入检查清单

新增一个服务时，需要完成四处改动：

| # | 改动位置            | 内容                                                                 |
|---|---------------------|----------------------------------------------------------------------|
| 1 | 服务自身            | 带 `application-coverage.yml` 配置，声明自己的 classes/source 目录   |
| 2 | 服务自身            | 提供 `start-with-coverage.sh`，以 tcpserver 模式注入 jacocoagent     |
| 3 | coverage-controller | **运行时注册接口**（推荐，无需改配置重启）；或写入 `application.yml` |
| 4 | 启动流水线          | 用 `EXTRA_SERVICES` 声明该服务，或手工启动                           |

骨架已内置第 1、2 步所需的文件（见下文各骨架说明），生成工程后无需再补。

### 1.1 注册到控制器：两种方式

**方式 A：运行时注册（推荐）**

服务启动后，调用控制器的注册接口即可， **不需要改配置文件、不需要重启控制器**：

```bash
curl -X POST http://127.0.0.1:8099/api/coverage/services \
  -H 'Content-Type: application/json' \
  -d '{
        "name": "order",
        "host": "127.0.0.1",
        "agentPort": 6302,
        "classesDirectories": [
          "ian-ddd-order/ian-ddd-order-trigger/target/classes",
          "ian-ddd-order/ian-ddd-order-domain/target/classes"
        ],
        "sourceDirectories": [
          "ian-ddd-order/ian-ddd-order-trigger/src/main/java"
        ],
        "replace": true
      }'
```

或使用流水线脚本的等价封装：

```bash
coverage-e2e.sh register order 6302 \
  "ian-ddd-order/ian-ddd-order-trigger/target/classes,ian-ddd-order/ian-ddd-order-domain/target/classes" \
  "ian-ddd-order/ian-ddd-order-trigger/src/main/java"
```

接口一览：

| 方法     | 路径                            | 说明                                           |
|----------|---------------------------------|------------------------------------------------|
| `POST`   | `/api/coverage/services`        | 注册；`replace=true` 时覆盖同名服务            |
| `GET`    | `/api/coverage/services`        | 列出当前生效的全部服务（含来源标记与目录告警） |
| `DELETE` | `/api/coverage/services/{name}` | 注销运行时注册的服务                           |

行为要点：

- **注册即生效**：下一次 `dump` / `finish` 就会采集并统计该服务，无需重启。
- **自动落盘**：注册记录写入 `<工作目录>/services/<name>.properties`，控制器重启后自动恢复。
- **目录缺失不拦截**：`classesDirectories` 尚未构建时注册会成功，但返回 `warnings` 提示；
  构建完成后用 `replace=true` 再注册一次即可。
- **配置来源不可注销**：`application.yml` 里声明的服务不能通过接口删除，避免运行时状态与配置文件不一致。

失败语义：

| 情况                                                  | HTTP  |
|-------------------------------------------------------|-------|
| 参数非法（名字含非法字符、端口越界、缺 classes 目录） | `400` |
| 名字已存在且未声明 `replace=true`                     | `409` |
| 注销配置文件中的服务                                  | `400` |
| 注销不存在的服务                                      | `404` |

**方式 B：写入 application.yml**

适合长期稳定的核心服务，改动后需要重启控制器：

```yaml
coverage:
  agents:
    - name: order
      host: 127.0.0.1
      port: 6302
```

两种方式可以混用：配置文件里的服务是基线，运行时注册的与之同名时会覆盖基线。

---

## 二、认证服务（scaffold-std）生成的工程

认证服务骨架已经内置两个文件， **无需额外配置**：

```text
<你的工程>/
├── docs/dev-ops/start-with-coverage.sh          # 以 tcpserver 模式启动，Agent 默认 6301
└── <rootArtifactId>-boot/src/main/resources/
    └── application-coverage.yml                 # coverage 配置，目录按五模块结构写死
```

### 2.1 Agent 端口分配

端口是全局约定的，多个服务不能冲突：

| 服务     | Agent 端口 | 说明 |
|----------|------------|------|
| Gateway  | 6300       | 固定 |
| 认证服务 | 6301       | 固定 |
| 新服务 A | 6302       | 顺延 |
| 新服务 B | 6303       | 顺延 |

启动时用环境变量覆盖即可：

```bash
COVERAGE_AGENT_NAME=order COVERAGE_AGENT_PORT=6302 \
  <你的工程>/docs/dev-ops/start-with-coverage.sh
```

`COVERAGE_AGENT_NAME` 必须与控制器里注册的 Agent `name` 一致，否则报告会归错服务。

### 2.2 服务侧配置

`application-coverage.yml` 的内容（骨架已生成，此处说明含义）：

```yaml
coverage:
  reports:
    - name: order                       # 服务名，与控制器配置一致
      classes-directories:
        - ../../../<rootArtifactId>-trigger/target/classes
        - ../../../<rootArtifactId>-domain/target/classes
        - ../../../<rootArtifactId>-infrastructure/target/classes
        - ../../../<rootArtifactId>-api/target/classes
      source-directories:
        - ../../../<rootArtifactId>-trigger/src/main/java
        # ... 其余模块同理
```

两个要点：

- **这些目录由控制器读取**，服务自己不用；相对路径基于 `boot` 模块的启动目录解析。
- 报告只分析这里列出的目录。新加模块（例如拆分出 `-query` 模块）时记得同步补上。

### 2.3 注册到控制器

推荐用运行时注册接口（见 1.1 节），服务启动后执行一次即可：

```bash
coverage-e2e.sh register order 6302 \
  "ian-ddd-order/ian-ddd-order-trigger/target/classes,ian-ddd-order/ian-ddd-order-domain/target/classes,ian-ddd-order/ian-ddd-order-infrastructure/target/classes,ian-ddd-order/ian-ddd-order-api/target/classes" \
  "ian-ddd-order/ian-ddd-order-trigger/src/main/java,ian-ddd-order/ian-ddd-order-domain/src/main/java,ian-ddd-order/ian-ddd-order-infrastructure/src/main/java,ian-ddd-order/ian-ddd-order-api/src/main/java"
```

若希望长期固化，则写入 `coverage-controller/src/main/resources/application.yml`：

```yaml
coverage:
  agents:
    - name: gateway
      host: 127.0.0.1
      port: 6300
    - name: std
      host: 127.0.0.1
      port: 6301
    - name: order                    # 新增
      host: 127.0.0.1
      port: 6302
  reports:
    - name: gateway
      classes-directories:
        - ian-ddd-gateway/gateway-app/target/classes
      source-directories:
        - ian-ddd-gateway/gateway-app/src/main/java
    - name: order                    # 新增
      classes-directories:
        - ian-ddd-order/ian-ddd-order-trigger/target/classes
        - ian-ddd-order/ian-ddd-order-domain/target/classes
        - ian-ddd-order/ian-ddd-order-infrastructure/target/classes
        - ian-ddd-order/ian-ddd-order-api/target/classes
      source-directories:
        - ian-ddd-order/ian-ddd-order-trigger/src/main/java
        - ian-ddd-order/ian-ddd-order-domain/src/main/java
        - ian-ddd-order/ian-ddd-order-infrastructure/src/main/java
        - ian-ddd-order/ian-ddd-order-api/src/main/java
```

模块名遵循 `<rootArtifactId>-<分层>` 约定，生成的工程目录就是这个名字（例如 `ian-ddd-order-trigger`）。
`classes-directories` 的相对路径以 **工作区根目录**为基准（同时包含 `ddd-base` 与各业务工程的目录）。

---

## 三、网关（scaffold-gateway）生成的工程

网关是单模块工程，骨架已内置：

```text
ian-ddd-order-gateway/
├── dev-ops/start-with-coverage.sh               # Agent 默认 6300
└── src/main/resources/application-coverage.yml  # classes 目录为 target/classes
```

生成的 `application-coverage.yml`：

```yaml
coverage:
  reports:
    - name: ian-ddd-order-gateway
      classes-directories:
        - target/classes
      source-directories:
        - src/main/java
```

控制器侧注册（假设网关工程位于工作区的 `ian-ddd-order-gateway`，Agent 端口 6300）：

```yaml
coverage:
  agents:
    - name: ian-ddd-order-gateway
      host: 127.0.0.1
      port: 6300
  reports:
    - name: ian-ddd-order-gateway
      classes-directories:
        - ian-ddd-order-gateway/target/classes
      source-directories:
        - ian-ddd-order-gateway/src/main/java
```

多个网关共存时，`agents[].port` 与报告 `name` 都要区分开，避免覆盖率归错服务。

---

## 四、编写 E2E 测试

测试写在 **被测入口工程**里（通常是网关），通过 HTTP 调用业务接口，服务链路自然被覆盖。

### 4.1 加依赖

在被测入口工程的 `pom.xml`（test scope）：

```xml
<dependency>
    <groupId>cn.iantech</groupId>
    <artifactId>coverage-junit-extension</artifactId>
    <scope>test</scope>
</dependency>
```

父 pom 的 `dependencyManagement` 需导入：

```xml
<dependency>
    <groupId>cn.iantech</groupId>
    <artifactId>coverage-junit-extension</artifactId>
    <version>${ddd.coverage.version}</version>
</dependency>
```

### 4.2 加配置

`src/test/resources/covers-e2e.properties`：

```properties
coverage.controller.url=http://127.0.0.1:8099
coverage.agents=gateway,std,order        # 参与采集的 Agent，留空表示全部
coverage.fail-on-error=false             # 控制器不可用时是否让测试失败
```

### 4.3 加注解

```java

@CoversE2e(value = "order-create-flow", buildId = "local")
@EnabledIfEnvironmentVariable(named = "RUN_COVERAGE_E2E", matches = "true")
class OrderCreateE2eTest {

    @Test
    void shouldCreateOrderThroughGateway() throws Exception {
        // 只调网关 HTTP 接口，Dubbo 链路会被各服务自己的 Agent 记录
    }
}
```

扩展会自动完成生命周期：

| 阶段        | 动作                                                     |
|-------------|----------------------------------------------------------|
| `beforeAll` | 创建 Session → reset 全部（或指定）Agent                 |
| `afterEach` | dump 全部 Agent（测试失败也会执行）                      |
| `afterAll`  | finish：merge + 生成报告 + 打印覆盖率与 classId 校验结果 |

用 `@EnabledIfEnvironmentVariable` 做开关，保证常规 CI 不受影响。

---

## 五、接入启动流水线

### 5.1 追加服务

`coverage-e2e.sh` 通过 `EXTRA_SERVICES` 支持任意数量的追加服务：

```bash
EXTRA_SERVICES="order:ian-ddd-order:6302" ./coverage-e2e.sh
```

格式：`名称:项目目录:Agent端口[:健康检查URL]`，逗号分隔可写多个。

- 项目目录为绝对路径或相对工作区根目录
- 服务需自带 `docs/dev-ops/start-with-coverage.sh` 或 `dev-ops/start-with-coverage.sh`
- 省略健康检查 URL 时只等 Agent 端口就绪（适合纯 Dubbo 服务）

带健康检查的例子（服务有 web 端口时更可靠）：

```bash
EXTRA_SERVICES="order:ian-ddd-order:6302:http://127.0.0.1:8093/actuator/health" \
  ./coverage-e2e.sh
```

流水线会自动完成：构建 → 启动 → 等就绪 → 跑测试 → 打印报告 → 关停（含追加服务）。

### 5.2 只跑某个服务的测试

服务侧独立跑（控制器需已启动）：

```bash
RUN_COVERAGE_E2E=true mvn -f <你的工程>/pom.xml test -Dtest=OrderCreateE2eTest
```

---

## 六、验证接入是否成功

```bash
# 1. 起控制器与全部服务
EXTRA_SERVICES="order:ian-ddd-order:6302" ./coverage-e2e.sh --keep

# 2. 确认 Agent 全部可连接
curl -s -X POST http://127.0.0.1:8099/api/coverage/sessions \
  -H 'Content-Type: application/json' -d '{"name":"check"}' | grep -o '"agentName":"[^"]*"\|"reachable":[a-z]*'
```

期望看到每个服务一行 `"agentName":"<name>","reachable":true`。

```bash
# 3. 看 dashboard 是否出现新服务
./coverage-e2e.sh report
```

报告里出现新服务的独立页面，说明接入完成。

---

## 七、常见问题

### 新服务的覆盖率是 0%

按顺序排查：

1. **看控制器日志的 classId 校验结果。** 出现「classId 校验未通过」说明被测进程与报告目录不同源——
   `target/classes` 在服务启动后被重新编译过。 **clean 重建后重启服务**即可。
2. **确认 Agent 名称一致。** `COVERAGE_AGENT_NAME` 与控制器里的 `name` 不一致，`dump` 会成功但报告归不到该服务。
3. **确认 `classes-directories` 覆盖了所有模块。** 少列一个模块，该模块的类就不会出现在报告里。
4. **确认 Dubbo 调用真的发生了。** 纯 health check 不会覆盖业务链路，需要在测试里真实调用接口。

### 注册接口返回 400 / 409

| 现象                             | 原因                       | 处理                                      |
|----------------------------------|----------------------------|-------------------------------------------|
| `400` 且提示 name 只允许字母数字 | 服务名含非法字符           | 换用 `[A-Za-z0-9._-]`，且以字母或数字开头 |
| `400` 且提示 agentPort           | 端口越界                   | 端口需在 1~65535                          |
| `400` 且提示 classesDirectories  | 未提供分析目录             | 至少给一个 `classes-directories`          |
| `409` 且提示 replace=true        | 同名服务已存在             | 加 `"replace":true` 覆盖，或换名字        |
| `400/404` 注销时                 | 服务来自 `application.yml` | 配置文件中的服务不可注销，需改配置        |

注册成功但返回非空 `warnings` 时，说明声明的目录当前不存在。这不是致命问题：目录构建完成后
再注册一次（带 `replace=true`）即可，或者直接开始采集，`finish` 阶段会跳过不存在的目录并告警。

### 新增服务后报告里没有它

先确认注册是否生效：

```bash
coverage-e2e.sh list
# 期望看到该服务，且「来源」为「运行时注册」
```

若列表里没有，说明注册未成功；若列表里有但报告里没有，检查该服务的 `classes-directories` 是否指向了
已经编译的目录（`target/classes` 必须存在，且与运行中的服务是同一次构建产物）。

### 端口冲突

Agent 端口、服务端口、控制器端口三者独立。冲突时用环境变量覆盖：

```bash
COVERAGE_AGENT_PORT=6304 SERVICE_PORT=8093 ./start-with-coverage.sh
```

`coverage-e2e.sh` 在启动前会自动关停占用上述端口的进程；如果不想让它动其他进程，请先手工确认端口占用情况。

### 两个服务出现同名类

整体报告要求 **各服务类名不冲突**。若新服务与既有服务存在相同 FQCN，整体报告里后加载的会覆盖前者。

排查方式：

```bash
# 检查两个服务的 class 名单是否有交集
comm -12 <(cd 服务A/target/classes && find . -name '*.class' | sort) \
         <(cd 服务B/target/classes && find . -name '*.class' | sort)
```

本项目 `cn.iantech.gateway.*` 与 `cn.iantech.{api,domain,cases,infrastructure,trigger}.*` 已错开，无冲突。
新服务如果落在相同包名下，需要调整包名或改为分服务报告。

### 服务启动脚本找不到

流水线会在两个位置查找：`docs/dev-ops/start-with-coverage.sh`（认证服务骨架）与
`dev-ops/start-with-coverage.sh`（网关骨架）。都没有时会直接报错并给出路径。

### 多副本部署（K8s）

同一服务的多个 Pod 各自持有独立 Agent。控制器连的是 Service 名时会随机命中某个 Pod，需要在
`coverage.agents` 里按 Pod IP 逐个注册，或改为由测试框架在部署后动态注册。

---

## 八、可选：接入独立模式（不做聚合）

如果只想采集 **单个服务自身**的覆盖率、不做跨服务合并，可以不接控制器：

```java
public class OrderServiceTestBase {
    private static final ExecFileLoader LOADER = new ExecFileLoader();

    @BeforeAll
    static void startAgent() throws Exception {
        // 直接用 jacocoagent 的 tcpserver，测试结束时 dump 到本地文件
    }
}
```

但这种方式拿不到「一次 E2E 经过所有服务」的整体视图，无法回答「这个用例覆盖了哪些服务」，
因此本项目采用控制器 + 各服务独立 Agent 的方案。
