# ddd

DDD 基座、共享 RPC 契约、认证服务实现与网关参考应用的聚合工程。

本仓库承载可运行的「实现」：通用基础组件、按服务拆分的 RPC 契约、一个完整的认证服务（Auth / RBAC / Customer / Channel）与一个网关参考应用。
工程骨架（Maven Archetype）独立维护在同级的 [`ddd-scaffold`](https://github.com/IanGall/ddd-scaffold) 仓库，避免骨架与参考实现漂移。

## 仓库结构

```text
ddd/
├── ddd-base/                      # 基础组件、依赖 BOM 与构建治理
│   ├── ddd-common/                # 通用响应、分页模型、持久化基类、常量与应用异常
│   ├── ddd-context/               # 协议无关请求上下文：core / dubbo / web 适配
│   ├── ddd-redis-starter/         # 技术无关 Redis API 与 Redisson 自动装配
│   ├── ddd-id-generator-starter/  # Redis 租约分配 WorkerId（可单生成器或按业务分块）+ 全局唯一 64 位 ID
│   ├── ddd-dependencies/          # 第三方依赖版本 BOM
│   ├── ddd-base-bom/              # 汇总 BOM（第三方 + 基础组件 + 契约版本）
│   └── ian-ddd-coverage/          # 分布式 E2E 覆盖率基础设施
├── ian-ddd-api/                   # API 契约聚合工程（按内部/外部分类，独立于服务实现）
│   ├── ian-ddd-api-internal/      #   内部 API 分类聚合：按服务下挂 <service>-api
│   │   └── ian-ddd-auth-api/      #     认证服务的 Dubbo RPC 契约（Auth / RBAC / Customer / Channel）
│   └── ian-ddd-api-external/      #   外部 API：对外/第三方 HTTP 契约（声明式 @HttpExchange）
├── ian-ddd-auth/                  # 认证服务实现：Auth / RBAC / Customer / Channel（HTTP 8091）
└── ian-ddd-gateway/               # 网关参考应用：HTTP 接入 + Dubbo 消费端（HTTP 8092）
```

各子工程的职责、约束与使用方式见对应文档：

- [ddd-base/README.md](ddd-base/README.md) — 基础组件职责、下游 BOM 与 Starter 接入
- [ian-ddd-api/README.md](ian-ddd-api/README.md) — 契约聚合工程、子模块拆分与版本约定
- [ian-ddd-auth/README.md](ian-ddd-auth/README.md) — 认证服务能力、安全边界、ID 生成与本地启动
- [ian-ddd-gateway/README.md](ian-ddd-gateway/README.md) — 网关接口、认证流程、HMAC 与错误语义
- [ddd-base/ian-ddd-coverage/README.md](ddd-base/ian-ddd-coverage/README.md) — 两套覆盖率口径、门槛配置与 E2E 流水线

## 技术栈

| 组件                | 版本    | 说明                                             |
|---------------------|---------|--------------------------------------------------|
| JDK                 | 21      | 构建时由 Maven Enforcer 强制校验                 |
| Spring Boot         | 4.1.1   | 各服务统一版本                                   |
| Dubbo               | 3.3.6   | Triple 协议，网关以 `@DubboReference` 调用认证服务 |
| Nacos Client        | 3.2.3   | 注册中心                                         |
| MyBatis Spring Boot | 4.1.0   | 持久化                                           |
| Redisson            | 4.7.0   | Redis 客户端，由 `ddd-redis-starter` 自动装配    |
| ShardingSphere      | 5.5.3   | 分库分表（user_order 2 库 × 4 表）               |
| XXL-Job             | 3.4.2   | 定时任务                                         |
| Kafka               | 由 Boot 管理 | 消息监听（trigger，`kafka.enabled` 可关闭）  |
| Spring Security Crypto | 由 Boot 管理 | BCrypt 密码编码                              |
| JUnit               | 6.1.3   | 单元测试与集成测试                               |
| JaCoCo              | 0.8.13  | 单服务覆盖率门槛与 E2E 采集                      |

依赖版本只在 `ddd-base/ddd-dependencies` 与 `ddd-base/ddd-base-bom` 中维护，业务模块不重复声明。

## 调用关系

```text
┌──────────┐   HTTP :8092    ┌─────────────────┐   Dubbo Triple   ┌───────────────────────────┐
│  调用方   │ ──────────────▶ │ ian-ddd-gateway │ ───────────────▶ │ ian-ddd-auth     │
└──────────┘                 │  认证 / 路由     │                  │ Auth/RBAC/Customer/Channel │
                             └─────────────────┘                  └───────────────────────────┘
                                      │                                        │
                                      └───────────────┬────────────────────────┘
                                                      ▼
                                      Nacos :8848 · Redis :6379 · MySQL :3306
```

- 网关只做 HTTP 接入与认证转发，不连接 Redis、不保存会话；Opaque Token 与登录风控由认证服务的 Auth 统一持有。
- 认证服务同时以 Dubbo Provider 注册（应用名 `ian-ddd-auth`，Triple 端口 50051）并暴露 HTTP（8091）。
- 服务间只有明文 Triple RPC，注册中心通过用户名/密码认证，生产环境凭据由部署环境注入。

## 环境要求

- JDK 21、Maven 3.9+。
- 本地启动完整链路需要：Nacos 8848、Redis 6379、MySQL 3306。
- 主源码与测试源码的方法名必须使用英文 lowerCamelCase，构建时由 Checkstyle 强制校验。

## 构建

在 `ddd` 仓库根目录执行，一次构建全部模块：

```bash
mvn -B clean install
```

只构建单个子工程（需已完成上述安装，保证 BOM 与基础组件可用）：

```bash
mvn -B -f ian-ddd-gateway/pom.xml clean verify
mvn -B -f ian-ddd-auth/pom.xml clean verify
```

## 本地启动

### 1. 准备中间件与数据库

启动 Nacos、Redis、MySQL 后，按需执行 `ian-ddd-auth/docs/dev-ops/environment/sql/` 下的初始化脚本：

| 脚本                                   | 用途                             |
|----------------------------------------|----------------------------------|
| `nacos.sql`                            | Nacos 配置表                     |
| `ian_dev_tech_db_00.sql` / `_01.sql`   | 分片库（含 `测试用户`、`SKU-100001` 等示例数据） |
| `rbac.sql` / `rbac-auth-hardening-v2.sql` | RBAC 账号与权限数据           |
| `customer-user.sql`                    | C 端用户数据                     |
| `channel-credential.sql`               | 渠道凭证数据                     |
| `xxl_job.sql`                          | XXL-Job 调度库                   |

认证服务还要求 Redis（会话、登录风控、防重放），连接信息通过 `MYSQL_*`、`REDIS_*`、`DUBBO_REGISTRY_*`
环境变量注入；本地开发统一放在仓库根 `.env.local`（不入库，模板 `.env.example`，启动脚本会自动加载）。

### 自动化测试专用库

E2E 覆盖率流水线（`coverage-e2e.sh`）与依赖真实中间件的测试统一以 `dev,autotest` 两个 Profile 启动，
`autotest`（`ian-ddd-auth-boot/src/main/resources/application-autotest.yml`）把 MySQL/Redis 指向测试库，
dev 库不会被测试写入：

| 用途                            | dev 库                     | 测试库（autotest）          |
|---------------------------------|----------------------------|-----------------------------|
| RBAC / 客户 / 渠道（`ds_rbac`） | `ddd_rbac`                 | `ddd_rbac_test`             |
| user_order 分片                 | `ian_dev_tech_db_00/01`    | `ian_test_tech_db_00/01`    |
| Redis                           | db 0                       | db 1                        |

建库方式：`ddd_rbac_test` 用 `ian-ddd-auth-boot/src/test/resources/sql/schema-rbac-mysql.sql`；
两个分片库用上表的 `ian_dev_tech_db_0{0,1}.sql` 并把库名 `ian_dev` 换成 `ian_test`。

### 2. 启动认证服务

本地开发凭证统一放在仓库根 `.env.local`（不入库，模板 `.env.example`；启动脚本自动加载），首次准备：

```bash
cp .env.example .env.local
# 至少填写：MYSQL_PASSWORD / REDIS_PASSWORD / DUBBO_REGISTRY_PASSWORD /
#           CHANNEL_ENCRYPTION_MASTER_KEY / PLATFORM_ADMIN_TOKEN
```

在 `ian-ddd-auth` 目录执行：

```bash
mvn -q spring-boot:run -pl ian-ddd-auth-boot -Pdev
```

生产环境不使用 `.env.local`：由部署平台注入同名环境变量（`SPRING_PROFILES_ACTIVE=prod` 已由 Dockerfile 固定，
非本地 Profile 使用示例令牌或全零渠道密钥会被启动校验直接拒绝）。

### 3. 启动网关

在 `ddd` 仓库根目录执行（凭证同样从 `.env.local` / 环境变量读取）：

```bash
export DUBBO_REGISTRY_ADDRESS='nacos://127.0.0.1:8848'
export DUBBO_REGISTRY_USERNAME='nacos-user'
export DUBBO_REGISTRY_PASSWORD='本地 Nacos 密码'
mvn -q -f ian-ddd-gateway/gateway-app/pom.xml spring-boot:run
```

健康检查无需认证：

```bash
curl "http://127.0.0.1:8092/actuator/health"
```

开户、登录、渠道 HMAC 等接口示例见 [ian-ddd-gateway/README.md](ian-ddd-gateway/README.md)。

## 覆盖率

本仓库有两套覆盖率，分母不同、不可互相替代，也不能相加：

| 口径         | 命令                                          | 说明                                                                 |
|--------------|-----------------------------------------------|----------------------------------------------------------------------|
| 单服务覆盖率 | `mvn -B install -Pcoverage-gate`              | 各模块自己的单元测试覆盖 `src/main`，分档门槛，无外部依赖              |
| 分布式 E2E   | `ddd-base/ian-ddd-coverage/coverage-e2e.sh`   | 从网关发起的真实跨服务调用链，各服务 JVM 内 JaCoCo Agent 采集后 merge 为并集 |

单服务门槛当前分档：基础组件与网关 60%，认证服务 `domain` 35%、`infrastructure` 15%、`trigger` 30%；契约模块以 `ApiContractTest` 守卫契约边界，不设行覆盖率门槛。
E2E 并集行覆盖率目标 40%（脚本内软提示，`COVERAGE_MIN_RATIO` 可调），需要 Nacos、Redis、MySQL 与服务全部启动。

> 登录接口带 IP 风控：同一 IP 60 秒内超过 30 次登录尝试会返回 `AUTH_RATE_LIMITED`，连续重跑 E2E 请间隔 60 秒以上。

门槛定义位置、报告产物与接入新服务的方式见
[ddd-base/ian-ddd-coverage/README.md](ddd-base/ian-ddd-coverage/README.md)。

## CI

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) 在 push 任意分支与 PR 时触发：JDK 21（temurin）下执行
`mvn -B install -Pcoverage-gate`，并上传各模块 JaCoCo 报告。分布式 E2E 不在 CI 运行（需要真实中间件与服务），
本地或专用流水线执行。

## 关联仓库

| 仓库                              | 内容                                            | 关系                                                         |
|-----------------------------------|-------------------------------------------------|--------------------------------------------------------------|
| [`ddd`](https://github.com/IanGall/ddd)（本仓库） | 基座 + 认证服务实现 + 网关参考应用 | 提供基座制品与参考实现                                       |
| [`ddd-scaffold`](https://github.com/IanGall/ddd-scaffold) | `scaffold-std` / `scaffold-gateway` 两个 Maven Archetype | 从骨架生成新服务与新网关；需与 `ddd` 同级检出后构建 |
