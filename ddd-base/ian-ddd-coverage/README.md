# ian-ddd-coverage

Dubbo 微服务分布式 E2E 覆盖率基础设施。测试只写在 Gateway 层，覆盖率由各服务 JVM 内的 JaCoCo Agent 独立采集，
再由控制器统一 dump、merge、生成报告。

## 本仓库有两套覆盖率，先看清该看哪个

|            | 单服务覆盖率（构建内置）                                 | 分布式 E2E 覆盖率（本模块）                                   |
|------------|----------------------------------------------------------|---------------------------------------------------------------|
| 怎么跑     | `mvn verify -Pcoverage-gate`                             | `ddd-base/ian-ddd-coverage/coverage-e2e.sh`                   |
| 测什么     | 各模块**自己的单元测试**执行路径                         | 从 Gateway 发起的**真实跨服务调用链**                         |
| 覆盖范围   | 各模块 `src/main`（`ddd-base` + std + gateway 已配门槛） | Gateway + 标准服务的 trigger/domain/infrastructure/api        |
| 外部依赖   | 无（离线可跑、秒级反馈）                                 | Nacos / Redis / MySQL + 服务全部启动（约 40 秒）              |
| 阈值       | 见下「门槛配置」                                         | 并集行覆盖目标 40%（脚本内软提示，`COVERAGE_MIN_RATIO` 可调） |
| 数字从哪看 | `target/site/jacoco/index.html`                          | `coverage/sessions/<id>/reports/index.html`                   |

**两者分母不同、不可互相替代**：单服务覆盖率保护 `ddd-common`、`ddd-redis-starter`、`ddd-id-generator-starter`
这类 **只被单元测试覆盖**的基础模块；E2E 覆盖率则覆盖 `trigger`/`api` 这些 **只在真实调用链里才会执行**的适配层。
把两者相加或对比大小都没有意义。

### 门槛配置

单服务门槛按模块分档，阈值取「当前覆盖率向下取整到 5%」，只挡回退、不阻塞正常演进：

| 模块                                                                              | 阈值     | 当前实测             |
|-----------------------------------------------------------------------------------|----------|----------------------|
| `ddd-common` / `ddd-context-*` / `ddd-redis-starter` / `ddd-id-generator-starter` | 60%      | 70% – 98%            |
| `coverage-controller` / `coverage-junit-extension`                                | 60%      | 81% / 60.2%          |
| `ian-ddd-gateway`（gateway-app）                                                  | 60%      | 74.0%                |
| `ian-frame-archetype-std-domain`                                                  | 35%      | 40.6%                |
| `ian-frame-archetype-std-infrastructure`                                          | 15%      | 21.1%                |
| `ian-frame-archetype-std-trigger` / `-api`                                        | 暂无门槛 | 尚无测试，补测后再加 |

门槛定义位置：

- `ddd-base/pom.xml` → `coverage-gate` profile：通用 BUNDLE 60% 规则
- `ian-ddd-gateway/pom.xml` → 同 id profile，`includes` 限定到 gateway-app
- `ian-ddd-archetype-std/pom.xml` → 同 id profile，domain / infrastructure 各一条规则

> 同 id profile 在子工程中 **覆盖**父工程的 `<rules>`：子工程声明的规则集整体替换父工程的规则集，不会叠加。
> 因此子工程必须写全自己需要的规则。
>
> 规则作用域用 `includes` 限定到具体模块；未命中的模块（如 std 的 trigger / api）不受该规则约束，
> 加了测试之后同样可以正常跑 `-Pcoverage-gate`。

给新模块加门槛：在所属父 pom 的 `coverage-gate` profile 里追加一条 `<rule>`，
用 `<includes><include>groupId:artifactId</include></includes>` 限定作用域，避免误伤其它模块。

JaCoCo 版本唯一来源：仓库根目录的 `.mvn/jacoco-version`（Shell 脚本读取），
升级时同步修改 `ddd-base/pom.xml` 的 `jacoco.version`。

```text
                    JUnit 测试（只调用 Gateway）
                              │ HTTP
                              ▼
                       ┌─────────────┐
                       │   Gateway   │  jacocoagent tcpserver :6300
                       └──────┬──────┘
                              │ Dubbo
                              ▼
                       ┌─────────────┐
                       │ 标准服务     │  jacocoagent tcpserver :6301
                       └─────────────┘

        coverage-controller 主动连接各 Agent，执行 reset / dump / merge / report
```

## 扩展新服务

从骨架扩展出新服务或新网关时，按 `EXTENDING.md` 接入即可：

1. 服务侧：骨架已内置 `application-coverage.yml` 与 `docs/dev-ops/start-with-coverage.sh`（网关在 `dev-ops/`）
2. 控制器侧： **运行时注册接口**（推荐，无需改配置重启）或写入 `application.yml`
3. 流水线：`EXTRA_SERVICES="服务名:项目目录:Agent端口[:健康检查URL]" ./coverage-e2e.sh`

新服务接入控制器，两种方式二选一：

```bash
# A. 运行时注册（推荐）：注册即生效，并落盘到 <工作目录>/services/，控制器重启自动恢复
coverage-e2e.sh register order 6302 \
  "ian-ddd-order/ian-ddd-order-trigger/target/classes,ian-ddd-order/ian-ddd-order-domain/target/classes" \
  "ian-ddd-order/ian-ddd-order-trigger/src/main/java,ian-ddd-order/ian-ddd-order-domain/src/main/java"

coverage-e2e.sh list              # 查看当前生效的服务
coverage-e2e.sh unregister order  # 注销（仅限运行时注册的服务）
```

```yaml
# B. 写配置文件：改动后需重启控制器
coverage:
  agents:
    - name: order
      host: 127.0.0.1
      port: 6302
  reports:
    - name: order
      classes-directories:
        - ian-ddd-order/ian-ddd-order-trigger/target/classes
```

HTTP 接口等价形式：

```bash
curl -X POST http://127.0.0.1:8099/api/coverage/services \
  -H 'Content-Type: application/json' \
  -d '{"name":"order","agentPort":6302,
       "classesDirectories":["ian-ddd-order/ian-ddd-order-trigger/target/classes"],
       "sourceDirectories":["ian-ddd-order/ian-ddd-order-trigger/src/main/java"],
       "replace":true}'
curl http://127.0.0.1:8099/api/coverage/services
curl -X DELETE http://127.0.0.1:8099/api/coverage/services/order
```

失败语义：参数非法 400、名称冲突 409（加 `replace=true` 覆盖）、配置文件中的服务不可注销（400/404）。

完整步骤、端口约定、验证方法与排查清单见 [`EXTENDING.md`](EXTENDING.md)。

## 模块

| 模块                       | 职责                                                                               |
|----------------------------|------------------------------------------------------------------------------------|
| `coverage-controller`      | 注册 Agent、编排 Session（start/reset/dump/finish）、合并 exec、生成 HTML/XML 报告 |
| `coverage-junit-extension` | `@CoversE2e` 注解与 JUnit 5 扩展，测试前后自动调用控制器                           |

## 快速开始

### 一键流水线（推荐）

```bash
# clean 构建 → 启动全部服务（带 Agent）→ 跑 E2E → 打印报告位置 → 自动关停服务
ddd-base/ian-ddd-coverage/coverage-e2e.sh

# 保留服务运行，便于手工翻报告
ddd-base/ian-ddd-coverage/coverage-e2e.sh --keep
```

其他子命令：

| 命令                     | 作用                                 |
|--------------------------|--------------------------------------|
| `coverage-e2e.sh`        | 全流程，结束时自动关停本次启动的服务 |
| `coverage-e2e.sh --keep` | 全流程，保留服务运行                 |
| `coverage-e2e.sh start`  | 只构建并启动服务                     |
| `coverage-e2e.sh test`   | 只跑测试（服务需已启动）             |
| `coverage-e2e.sh report` | 打印最近一次会话的报告位置           |
| `coverage-e2e.sh status` | 查看路径、服务状态与最近会话         |
| `coverage-e2e.sh stop`   | 停止全部服务                         |

可用环境变量：`COVERAGE_E2E_LOGIN_NAME` / `COVERAGE_E2E_LOGIN_PASSWORD`（不填则自动开户并缓存凭证）、
`COVERAGE_SKIP_BUILD=1`（跳过构建快速重跑）、`WORKSPACE_DIR`（覆盖工作区根目录探测）、
`COVERAGE_SPRING_PROFILES`（默认 `dev,autotest`，见下）。

前置条件：Nacos (8848)、Redis (6379)、MySQL (3306) 已启动，且**自动化测试库已建好**（脚本不做建库）。

服务以 `dev,autotest` 两个 Spring profile 启动（后者优先）：业务配置沿用 dev，但 MySQL/Redis 全部指向
自动化测试库，dev 库不会被 E2E 写入。对照关系与建库清单见 `application-autotest.yml`：

| 用途                          | 数据库                     |
|-------------------------------|----------------------------|
| RBAC / 客户 / 渠道（`ds_rbac`） | `ddd_rbac_test`            |
| user_order 分片                | `ian_test_tech_db_00/01`   |
| Redis                          | db 1（dev 为 db 0）        |

一次性初始化（建库 + 建表 + 样例数据）：`ddd_rbac_test` 用 `ian-frame-archetype-std-boot/src/test/resources/sql/schema-rbac-mysql.sql`；
两个分片库用 `ian-ddd-archetype-std/docs/dev-ops/environment/sql/ian_dev_tech_db_0{0,1}.sql`（把库名中的
`ian_dev` 换成 `ian_test` 后执行）。E2E 会自行开户，不依赖库内种子数据。

> `autotest` 必须放在 profile 列表最后（`dev,autotest`）才能覆盖 dev 的库地址：多个 profile 同时激活时后者优先。

> 登录接口带 IP 风控：同一 IP 60 秒内超过 30 次登录尝试会返回 `AUTH_RATE_LIMITED`。
> 连续重跑 E2E（一轮约 20 次登录）时，两轮之间请间隔 60 秒以上。

### 手动分步执行

#### 1. 启动覆盖率控制器

```bash
mvn -f ddd-base/ian-ddd-coverage/coverage-controller/pom.xml spring-boot:run
```

默认监听 `8099`，产物写入 `coverage/sessions/<sessionId>/`。

#### 2. 带 Agent 启动被测服务

```bash
# Gateway（Agent 端口 6300）
ian-ddd-gateway/dev-ops/start-with-coverage.sh

# 标准服务（Agent 端口 6301）
ian-ddd-archetype-std/docs/dev-ops/start-with-coverage.sh
```

两个脚本只是把 jacocoagent 以 `output=tcpserver` 注入 JVM，服务本身不需要感知控制器。

### 3. 运行 E2E 测试

以 Gateway 的测试为例，加上 `@CoversE2e` 注解即可：

```java
@CoversE2e(value = "create-order")
class OrderE2eTest {

    @Test
    void createOrder() {
        // 只调用 Gateway 的 HTTP 接口
    }
}
```

执行：

```bash
RUN_COVERAGE_E2E=true mvn -f ian-ddd-gateway/gateway-app/pom.xml test -Dtest=GatewayCoverageE2eTest
```

测试结束后控制台输出：

```text
[Coverage] 本次测试覆盖率汇总（Session 20260912-093808-9b1fe）
------------------------------------------------------------
    gateway: 行覆盖 82.0%（已覆盖 820/1000）
    std: 行覆盖 76.0%（已覆盖 760/1000）
    overall: 行覆盖 73.4%（已覆盖 1580/2000）
------------------------------------------------------------
报告入口: http://127.0.0.1:8099/api/coverage/sessions/20260912-093808-9b1fe/reports/index.html
```

## 目录产物

```text
coverage/sessions/<sessionId>/
├── gateway.exec          # 各服务原始 execution data
├── std.exec
├── overall.exec          # 合并结果
├── dashboard.html        # 服务级 + 整体覆盖率总览
└── reports/
    ├── index.html        # 整体 HTML 报告
    ├── overall.xml       # 供 CI 解析
    ├── gateway/index.html
    └── std/index.html
```

## classId 一致性校验

JaCoCo 按 `classId` 匹配 execution data 与字节码。当被测进程加载的字节码与生成报告所用的
`target/classes` 不是同一次构建产物时，classId 不一致， **JaCoCo 不报错，而是把该类当作未覆盖**，
覆盖率静默变成 0%。

`finish` 阶段会自动比对每个 Agent 上报的 classId 与分析目录的 classId，失配时：

- 控制器日志打出 `WARN`，列出失配的类与两边的 classId；
- `finish` 响应体新增 `consistency` 字段，测试侧扩展会在汇总后追加警告区块。

```text
[Coverage] 警告：classId 校验未通过
  Agent [std] 有 3 个类的字节码与报告目录不一致，这些类的覆盖率被误报为 0%：
    cn/iantech/cases/auth/service/AuthCaseService（被测进程 0x54b413fa02c17d1a / 报告目录 0xef85aaf9bd9516aa）
  原因：被测服务启动后源码被重新编译，或服务与报告使用了不同次构建的产物。
  处理：清理并重新构建后重启被测服务，再执行测试。
```

因此 **修改代码后必须 clean 重建再重启服务**；`coverage-e2e.sh` 默认就会先 clean 构建再启动。

## 控制器 API

| 方法 | 路径                                     | 说明                                  |
|------|------------------------------------------|---------------------------------------|
| POST | `/api/coverage/sessions`                 | 创建 Session，返回 id 与 Agent 连通性 |
| GET  | `/api/coverage/sessions/{id}`            | 查询 Session 状态                     |
| POST | `/api/coverage/sessions/{id}/reset`      | 清零探针计数，可传 `agentNames` 过滤  |
| POST | `/api/coverage/sessions/{id}/dump`       | 采集并累加各服务 exec                 |
| POST | `/api/coverage/sessions/{id}/finish`     | merge + 生成报告                      |
| POST | `/api/coverage/reports/merge`            | 合并多个已完成 Session 生成并集报告   |
| GET  | `/api/coverage/sessions/{id}/report`     | 跳转整体 HTML 报告                    |
| GET  | `/api/coverage/sessions/{id}/reports/**` | HTML/XML/dashboard 静态资源           |

## 并集报告（多测试类）

`@CoversE2e` 的 Session 以测试类为单位：`beforeAll` 创建并 reset，`afterAll` 汇总。
因此每个测试类结束时打印的覆盖率只代表该类的用例， **不代表整轮测试**。

一轮测试结束后，用合并接口得到并集：

```bash
curl -X POST http://127.0.0.1:8099/api/coverage/reports/merge \
  -H 'Content-Type: application/json' \
  -d '{"name":"e2e-run","sessionIds":["20260912-170837-4f066","20260912-170840-0ed5a"]}'
```

返回体与 `finish` 一致（`overall.lineRatio` 即整轮行覆盖率）。`coverage-e2e.sh`
已自动完成这一步：`all` / `test` 命令会把本轮新增的 Session 全部合并，
并在结尾打印「本轮 E2E 并集覆盖率」。

## 测试侧配置

`gateway-app/src/test/resources/covers-e2e.properties`：

```properties
coverage.controller.url=http://127.0.0.1:8099
coverage.agents=gateway,std
coverage.fail-on-error=false
```

| 配置项                              | 默认值                  | 说明                             |
|-------------------------------------|-------------------------|----------------------------------|
| `coverage.controller.url`           | `http://127.0.0.1:8099` | 控制器地址                       |
| `coverage.agents`                   | 空（全部）              | 参与采集的 Agent，逗号分隔       |
| `coverage.enabled`                  | `true`                  | 设为 false 整体关闭采集          |
| `coverage.fail-on-error`            | `false`                 | 控制器不可用等问题是否让测试失败 |
| `coverage.require-reachable-agents` | `true`                  | 存在不可达 Agent 时是否直接失败  |

系统属性与环境变量（`COVERAGE_CONTROLLER_URL`、`COVERAGE_AGENTS` 等）优先级高于配置文件。

## 设计要点

- **采集与测试解耦**：Dubbo 只负责业务请求，不传递 coverage 数据；每个 JVM 独立采集。
- **reset 语义**：JaCoCo 没有独立 reset 指令，采用 `dump(reset=true)`，Agent 会先返回数据再清零。
- **失败降级**：单个 Agent 不可达只记录失败，不中断流程；测试失败也会执行 dump（`afterEach` 注册）。
- **并发限制**：同一时刻只允许一个 RUNNING Session，避免多测试互相污染探针数据。
- **版本一致**：报告使用各服务的 `target/classes`，需保证被测进程与本地 class 来自同一次构建。
- **同名类**：整体报告要求各服务类名不冲突（本项目 `cn.iantech.gateway.*` 与 `cn.iantech.api/domain/trigger.*` 已满足）。

## 后续扩展

- 并发测试：如需 A/B 测试同时采集且互不干扰，需要按线程/请求维度隔离探针，属于 Agent 级改造。
- 多副本部署：K8s 场景不能通过 Service 名 dump（会随机命中一个 Pod），需要按 Pod IP 逐个采集后合并。
- 调用链关联：`ddd-context-dubbo` 已透传 `requestId` 等字段，可扩展 `coverageSessionId` 关联单测试用例经过的服务。
