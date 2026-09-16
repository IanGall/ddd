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
│   ├── ddd-id-generator-starter/  # Redis 租约分配实例级 WorkerId（业务各持生成器实例）+ 全局唯一 64 位 ID
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
| Dubbo               | 3.3.6   | `dubbo` 协议（默认 Hessian2 序列化），网关以 `@DubboReference` 调用认证服务 |
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
┌──────────┐   HTTP :8092    ┌─────────────────┐      Dubbo       ┌───────────────────────────┐
│  调用方   │ ──────────────▶ │ ian-ddd-gateway │ ───────────────▶ │ ian-ddd-auth     │
└──────────┘                 │  认证 / 路由     │                  │ Auth/RBAC/Customer/Channel │
                             └─────────────────┘                  └───────────────────────────┘
                                      │                                        │
                                      └───────────────┬────────────────────────┘
                                                      ▼
                                      Nacos :8848 · Redis :6379 · MySQL :3306
```

- 网关只做 HTTP 接入与认证转发，不连接 Redis、不保存会话；Opaque Token 与登录风控由认证服务的 Auth 统一持有。
- 认证服务同时以 Dubbo Provider 注册（应用名 `ian-ddd-auth`，`dubbo` 协议端口 20880）并暴露 HTTP（8091）。
- 服务间只有明文 Dubbo RPC，注册中心通过用户名/密码认证，生产环境凭据由部署环境注入。

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

> **导入这些脚本必须显式指定 `--default-character-set=utf8mb4`**：
>
> ```bash
> mysql --default-character-set=utf8mb4 -h 127.0.0.1 -u root -p ddd_rbac < rbac.sql
> ```
>
> 脚本文件本身是 UTF-8，但 `mysql` 客户端会用它**自己声明的连接字符集**去解释文件字节。若客户端跑在 latin1 下，
> 文件里的 UTF-8 字节（如「查看」= `E6 9F A5 E7 9C 8B`）会被当成 latin1 字符（`æŸ¥çœ‹`）再转存为 utf8mb4，
> 落库就变成**双重编码**的乱码（`C3A6C5B8C2A5…`，26 字节而不是 12 字节）。这种数据应用层无法自行纠正，
> 页面上的中文会显示成 `å` ê™...` 之类。2026-09-15 曾因一次备份恢复踩到这个坑（`ddd_rbac` 的 1 条账号 + 21 条权限名），
> 已用 `CONVERT(CAST(CONVERT(col USING latin1) AS BINARY) USING utf8mb4)` 修复。
>
> 通过接口开户（`POST /api/admin/platform/accounts`）不受影响：那条路径的权限名来自 `RbacPermissionCode`（Java，UTF-8）
> 且 JDBC URL 带 `characterEncoding=utf8`，落库是正确的。

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

## 部署到 Kubernetes

**一键脚本**（构建 jar → 构建镜像 → 刷新 ConfigMap/Secret → apply 清单 → 按需滚动 → 等就绪，可重复执行）：

```bash
bash scripts/deploy-local.sh                    # 默认：两个服务、dev profile、命名空间 ian-ddd、每服务 1 副本
bash scripts/deploy-local.sh --service auth     # 只更新认证服务（--service gateway 只更新网关）
bash scripts/deploy-local.sh --replicas keep    # 副本数按清单（HPA 2→8/2→6），需要验证 HPA 伸缩时用
bash scripts/deploy-local.sh status|logs|restart # 查看状态 / 看日志（--follow）/ 强制滚动重启
bash scripts/deploy-local.sh clean              # 清理工作负载与配置（--purge 连命名空间，--images 连本地镜像）
```

脚本要点：自动识别本机架构出镜像（`--profile prod` 时要求自备强密钥，本地开发值会被启动期校验拒绝）；**本地副本默认收敛到 1**
（同时把 HPA 的 `minReplicas` 同步为 1，否则 HPA 15 秒后就把手动 scale 改回清单里的 2；`--replicas N` 可指定其它值）；
给 Deployment 打「镜像 + 配置」摘要注解，内容没变就不重启 Pod；配置只由脚本管理（手工 `kubectl apply -f <目录>` 会覆盖按
profile 生成的 ConfigMap）。下面是不用脚本时的手工步骤与清单说明。

### 服务怎么访问

| 访问方 | 目标 | 地址 | 说明 |
| --- | --- | --- | --- |
| 宿主机 | **管理端前端 + 网关（同一 host）** | `http://gateway.example.com` | OrbStack 把 `infra/apisix-gateway` 这个 LoadBalancer 的端口映射到宿主 `127.0.0.1`。同一 host 下按路径分流：`/` → 前端 `ddd-web:80`，`/api` → `ian-ddd-gateway:8092`（同源，浏览器不需要 CORS） |
| 宿主机 | 网关（备用入口） | `http://127.0.0.1:31213` + `Host: gateway.example.com` | 同一个 LoadBalancer 的 nodePort，不依赖宿主端口映射 |
| 宿主机 | 网关（不依赖 apisix） | `kubectl port-forward -n ian-ddd svc/ian-ddd-gateway 8092:8092` → `http://127.0.0.1:8092` | 换集群或 CI 里用；探活也走这条 |
| 集群内 | 网关 | `http://ian-ddd-gateway.ian-ddd.svc.cluster.local:8092` | 绕过 Ingress，直接打 Service |
| 集群内 | 认证服务 | `ian-ddd-auth.ian-ddd.svc.cluster.local:20880`（Dubbo） | **只有 Dubbo，没有 HTTP**；正常路径是网关以 Dubbo 调用它 |
| 宿主机 | 认证服务（调试） | `kubectl port-forward -n ian-ddd svc/ian-ddd-auth 20880:20880` + Dubbo 直连 | provider 注册的是 podIP，本地直连要用直连模式 |
| 宿主机 | infra 中间件 | `127.0.0.1:3306`（MySQL）、`:6379`（Redis）、`:8848`/`:9848`（Nacos）、`:9092`（Kafka） | 同一套端口映射 |
| 集群内 | infra 中间件 | `<svc>.infra.svc.cluster.local` | 清单里的 `DUBBO_REGISTRY_ADDRESS` / `MYSQL_HOST` 等就是这些地址 |

```bash
# 一次业务调用（经 Ingress 到网关；用错密码即证明全链路通，401 AUTH_REQUIRED 是预期）
curl -H 'Content-Type: application/json' \
  -X POST -d '{"loginName":"nobody@1.com","password":"wrong-password"}' \
  http://gateway.example.com/api/admin/auth/login

# 前端的 SPA 由同一 host 的 / 提供（路由回退到 index.html，刷新深链不会 404）
curl -s -o /dev/null -w '%{http_code}\n' http://gateway.example.com/login      # 期望 200

# 探活不在 /api 下，经 Ingress 拿不到，用 port-forward 直连
kubectl port-forward -n ian-ddd svc/ian-ddd-gateway 8092:8092 &
curl http://127.0.0.1:8092/actuator/health                                     # 期望 200
```

四条要点：

- **认证服务没有可直连的 HTTP 入口**：它只暴露 Dubbo 20880（`server.port: 8091` 从不被监听），对外只有"经网关"这一条路；
  要用 HTTP 调它自己得先给 trigger 打开 `-Phttp`。
- **Ingress 的 host 是占位值** `gateway.example.com`：要用真实域名就往 `/etc/hosts` 加 `127.0.0.1 gateway.example.com`，
  或 `curl --resolve gateway.example.com:80:127.0.0.1`。
- **别用 `192.168.139.2`**（LoadBalancer 的 EXTERNAL-IP）——它在宿主机不可达。
- **404 先看响应体**：`{"error_msg":"404 Route Not Found"}` 是 **apisix 层**没匹配到 Host（检查 `Host` 头是不是 Ingress 声明过的域名，
  Apifox/Postman 会自动从 URL 生成 `Host`，改过 URL 后要把手动存下来的那条删掉）；`{"code":"NOT_FOUND","info":"请求路径不存在"}`
  才是网关自己的 404（路径不在白名单）。注意**只有 `/api/**` 会到达网关**，非 `/api` 路径由前端的 nginx 处理（见下）。
- **健康检查的边界**：网关 `/actuator/health` UP 只代表网关自身；认证服务 TCP 20880 通也不代表依赖就绪（MySQL 挂掉时它仍是
  `Ready`，业务请求会返回 504 `RPC_TIMEOUT`）。容器内自检：网关镜像有 `curl`，认证镜像只有 `bash`/`nc`
  （`nc -z -w 3 127.0.0.1 20880`，或用 `bash -c 'cat < /dev/null > /dev/tcp/127.0.0.1/20880'`）。

### 管理端前端（ddd-web）

管理端是一个独立仓的 SPA，**与网关共用 `gateway.example.com`**，靠路径分流做到同源：

| 路径 | 归属 | 服务 |
| --- | --- | --- |
| `/` | 管理端前端 | `ddd-web`（nginx 托管静态产物，SPA 路由回退到 `index.html`） |
| `/api` | 网关 | `ian-ddd-gateway:8092`（`/api/admin/**`、`/api/app/**`、`/api/external/**`） |

四条已定的边界（都基于实测，不是推测）：

- **网关的 Ingress 只认领 `/api`**，前端 Ingress 认领 `/`；**两边都不能写 `/`**——同一 host + 同一 path 出现在两个 Ingress 上，
  命中哪个后端是不确定的。
- **不需要 CORS**：网关没有任何 CORS 配置，预检 `OPTIONS` 还会被认证白名单过滤器拦成 `401`。靠「同 host 同源」+
  「开发期 dev-server 代理」解决，**不要**为了前端去给网关加 CORS。
- **静态资源不要挂到网关上**：网关有路径白名单（只认 `/api/admin|app|external/**`），静态资源由前端自己的 nginx 承载。
- **前端自己的约束**（开发服务器端口、代理、镜像与清单）由 `ddd-web/README.md` 与 `ddd-web/deploy/README.md` 维护，
  本仓只记录「网关这一侧」的责任——**同一事实不要在两处各写一份**。

三个可部署服务各自在模块内维护 k8s 清单（原生 YAML，`kubectl apply -f` 直接使用，不需要 Helm/Kustomize），权威说明在各自的
`k8s/README.md`：

| 服务 | 清单位置 | 入口 | 探针 |
|------|----------|------|------|
| 认证服务 `ian-ddd-auth` | [`ian-ddd-auth/docs/dev-ops/k8s/`](ian-ddd-auth/docs/dev-ops/k8s/README.md) | 仅集群内（被网关以 Dubbo 调用） | TCP 20880 |
| 网关 `ian-ddd-gateway` | [`ian-ddd-gateway/dev-ops/k8s/`](ian-ddd-gateway/dev-ops/k8s/README.md) | ClusterIP + Ingress（`/api`） | HTTP `/actuator/health` |
| 管理端前端 `ddd-web` | `ddd-web/deploy/k8s/`（见该仓 README） | ClusterIP + Ingress（`/`） | HTTP `/` |

每个目录包含 Deployment / Service / ConfigMap / `secret.yaml.example` / HPA / PDB（网关另有 Ingress），部署顺序为
**ConfigMap → Deployment → Service(/Ingress) → HPA → PDB**，凭证先在集群外用 `kubectl create secret generic ... --from-env-file`
创建（仓库是公开仓，真实凭证一律不入库）。

两点容易踩的事实：

- **认证服务没有 HTTP 端口**：`spring-boot-starter-web` 仅在 `-Phttp` 下引入且默认不激活，`application.yml` 的
  `server.port: 8091` 从不被监听；探针与端口映射都必须指向 Dubbo 20880，按 8091 配置会让 Pod 进入 CrashLoopBackOff。
- **HPA 需要 metrics-server，且 Deployment 必须有 `requests.cpu`**：本地 OrbStack 集群已装好（`kube-system/metrics-server`，
  带 `--kubelet-insecure-tls`）；没有 metrics-server 时 HPA 对象能创建但不会伸缩，`kubectl top` 也不可用。

## CI

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) 在 push 任意分支与 PR 时触发：JDK 21（temurin）下执行
`mvn -B install -Pcoverage-gate`，并上传各模块 JaCoCo 报告。分布式 E2E 不在 CI 运行（需要真实中间件与服务），
本地或专用流水线执行。

## 关联仓库

| 仓库                              | 内容                                            | 关系                                                         |
|-----------------------------------|-------------------------------------------------|--------------------------------------------------------------|
| [`ddd`](https://github.com/IanGall/ddd)（本仓库） | 基座 + 认证服务实现 + 网关参考应用 | 提供基座制品与参考实现                                       |
| [`ddd-scaffold`](https://github.com/IanGall/ddd-scaffold) | `scaffold-std` / `scaffold-gateway` 两个 Maven Archetype | 从骨架生成新服务与新网关；需与 `ddd` 同级检出后构建 |
| [`ddd-web`](https://github.com/IanGall/ddd-web) | 管理端单页应用（React + Vite + TS + Ant Design） | 网关 `/api/admin/**` 的第一方消费者；需与 `ddd` 同级检出；前端侧的部署形态与本地启动见其 README |
