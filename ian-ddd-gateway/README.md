# ian-ddd-gateway

本模块是可运行的网关参考应用；通用网关 Maven Archetype 位于 `ddd-scaffold/scaffold-gateway`。

```text
ian-ddd-gateway/
└── gateway-app       # 现有 RBAC 网关参考应用
```

## 参考应用

### 启动前准备

所有环境必须通过环境变量注入注册中心凭据（本地开发统一放在仓库根 `.env.local`，模板见 `.env.example`；
`dev-ops/start-with-coverage.sh` 会自动加载）：

```bash
export DUBBO_REGISTRY_ADDRESS='nacos://127.0.0.1:8848'
export DUBBO_REGISTRY_USERNAME='nacos-user'
export DUBBO_REGISTRY_PASSWORD='从密钥管理系统读取'
```

同时启动 `ian-ddd-auth` 并发布 `cn.iantech.api.IAuthService:1.0.0`。

认证会话由 Auth 服务统一保存和校验，Gateway 不再连接 Redis，也不保存本地 Session。RBAC 与 Customer 只作为管理员和 C 端用户的
身份校验提供方，不持有 Token 或 Session。

认证服务的 `IAuthService`、`IRbacService` 和 `IUserService` 全部显式声明 `throws AppException`。这样 Dubbo 会按声明式业务异常
原样传递语义码，Gateway 能区分令牌失效等业务失败与 Auth 服务不可用等基础设施故障。Gateway 的认证过滤器只负责把异常交给
Spring MVC 的统一异常解析器，不手写 JSON，也不分析 Dubbo 异常文本。

`POST /api/admin/platform/accounts` 只负责把 `X-Platform-Token` 和开户字段封装为强类型 RPC 请求；平台凭据由认证服务
Provider
最终校验，Gateway 不保存、不比较该凭据。Provider 未配置 `PLATFORM_ADMIN_TOKEN` 时拒绝启动。登录、刷新、注销和会话管理 按管理端
`/api/admin/auth/**` 与 C 端 `/api/app/auth/**` 分离，并由 Gateway 转发给 Auth；业务请求携带的是由 Auth 签发的 opaque
Bearer Token。Gateway 每个受保护请求调用 Auth 校验令牌后， 再恢复主账号和当前用户上下文，不采信外部 `X-Account-Id` 或
`X-User-Id`。Dubbo Triple 使用现有明文 RPC 连接，不启用 JWT、JWKS
或 mTLS。

### 编译与测试

以下命令默认在 `ddd` 仓库根目录执行：

```bash
mvn -q -f ian-ddd-gateway/gateway-app/pom.xml test
```

### 启动

```bash
mvn -q -f ian-ddd-gateway/gateway-app/pom.xml spring-boot:run
```

### 开户与登录示例

```bash
curl -X POST http://127.0.0.1:8092/api/admin/platform/accounts \
  -H 'Content-Type: application/json' \
  -H "X-Platform-Token: $PLATFORM_ADMIN_TOKEN" \
  -d '{"username":"root","password":"高强度主账号密码","displayName":"示例主账号"}'

LOGIN_RESPONSE=$(curl -s -X POST http://127.0.0.1:8092/api/admin/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"loginName":"root@主账号ID.com","password":"高强度主账号密码"}' \
)
ACCESS_TOKEN=$(printf '%s' "$LOGIN_RESPONSE" | jq -r '.data.accessToken')
REFRESH_TOKEN=$(printf '%s' "$LOGIN_RESPONSE" | jq -r '.data.refreshToken')
curl -H "Authorization: Bearer $ACCESS_TOKEN" \
  "http://127.0.0.1:8092/api/admin/rbac/users?pageNum=1&pageSize=20"

curl -s -X POST http://127.0.0.1:8092/api/admin/auth/refresh \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"
```

管理端业务接口固定使用 `/api/admin/**`，C 端业务接口固定使用 `/api/app/**`。两端请求均在 `Authorization` 请求头中携带
`Bearer <accessToken>`，Refresh Token 只提交给各自的 `/api/admin/auth/refresh` 或 `/api/app/auth/refresh`，不放入 URL
或业务请求头。注销和设备会话接口也分别位于两端的 `/auth/**` 子路径；C 端不建立逐用户 RBAC，最终授权由业务服务按可信
`customerId`、资源归属和有效绑定完成。

### 渠道 HMAC 请求

`/api/external/**` 只使用请求级 HMAC，不提供 Bearer Token 降级。渠道必须为每个请求提供以下五个请求头，认证通过后的固定
授权范围为 `external:access`：

```http
X-Channel-Code: ch_xxx
X-Channel-Secret-Version: 1
X-Channel-Timestamp: 1787107200
X-Channel-Content-SHA256: 64位小写十六进制
X-Channel-Signature: 64位小写十六进制
```

Canonical Request 依次连接大写 Method、规范化 Path、RFC 3986 排序后的 Query、规范化 Content-Type、渠道编码、 密钥版本、Unix
秒时间戳和原始 Body SHA-256，每项占一行；非空 JSON 的 Content-Type 固定为 `application/json`， 空 Body 的 Content-Type
为空。签名为 `hexLower(HMAC-SHA256(channelSecret, UTF8(canonicalRequest)))`。

网关限制原始 Body 最大 1 MiB，渠道认证 Cases 服务只接受当前时间前后 300 秒内的请求，并以
`channelCode + signature`
摘要在 Redis 登记 600
秒。完全相同的签名只能成功一次；重试必须更新秒级时间戳并重新签名。生产 HTTP 与 Dubbo 均不启用 TLS， HMAC
只能提供请求认证、完整性和有限防重放，不能加密请求或响应内容。

HTTP 错误统一返回 `{"code","info","data"}`，并保留 `X-Request-Id`。公共语义码和状态码映射如下：

| 响应码                                | HTTP 状态码 |
|---------------------------------------|------------:|
| `INVALID_ARGUMENT`                    |         400 |
| `AUTH_REQUIRED`                       |         401 |
| `ACCESS_DENIED`                       |         403 |
| `AUTH_REFRESH_BUSY`                   |         409 |
| `AUTH_RATE_LIMITED`                   |         429 |
| 其他明确业务异常                      |         422 |
| `RPC_ERROR`                           |         502 |
| `AUTH_UNAVAILABLE`、`RPC_NO_PROVIDER` |         503 |
| `RPC_TIMEOUT`                         |         504 |
| `INTERNAL_ERROR`                      |         500 |

客户端必须按 `SUCCESS` 等语义码判断结果，不再使用 `0000`～`0003` 数字码。认证失败只在 `AUTH_REQUIRED` 等业务码下返回；只有
无提供者、网络失败、超时或未识别的 Auth 运行时故障才返回服务不可用类错误。响应不会包含服务端堆栈。

健康检查无需认证：

```bash
curl "http://127.0.0.1:8092/actuator/health"
```

### 覆盖率

本工程有两个覆盖率数字，口径不同、互不可替代：

- **单服务覆盖率**（`mvn verify -Pcoverage-gate`）：本模块单元测试对 `src/main` 的覆盖，门槛 60%，不需要外部依赖。
- **分布式 E2E 覆盖率**：从 Gateway 发起的真实跨服务调用链，覆盖 Gateway + 认证服务，需要中间件与已启动的服务。

完整对照与门槛配置见 `ddd-base/ian-ddd-coverage/README.md`。

#### 分布式 E2E 覆盖率

`gateway-app` 已接入 `coverage-junit-extension`，测试只需标注 `@CoversE2e`，
即可自动采集 Gateway 与认证服务的 JaCoCo 覆盖率并生成报告。

推荐直接用一键流水线（自动构建、启停服务、跑全部 E2E 用例并输出 **并集**覆盖率）：

```bash
ddd-base/ian-ddd-coverage/coverage-e2e.sh
```

手工分步执行：

```bash
# 1. 启动覆盖率控制器（8099）
mvn -f ddd-base/ian-ddd-coverage/coverage-controller/pom.xml spring-boot:run

# 2. 带 jacocoagent 启动认证服务与 Gateway
ian-ddd-auth/docs/dev-ops/start-with-coverage.sh
ian-ddd-gateway/dev-ops/start-with-coverage.sh

# 3. 运行全部 E2E 覆盖率测试
RUN_COVERAGE_E2E=true mvn -f ian-ddd-gateway/gateway-app/pom.xml test -Dtest='GatewayCoverageE2eTest,*E2eTest'
```

`GatewayCoverageE2eTest` 是最小冒烟用例（健康检查 + 可选的真实登录）；完整业务链路在
`src/test/java/cn/iantech/gateway/e2e/` 下的 `*E2eTest`，覆盖 RBAC 生命周期、认证令牌轮换与重放防护、
C 端注册登录、渠道凭证管理与异常语义。

> 登录接口有 IP 风控：同一 IP 60 秒内 30 次登录尝试会返回 `AUTH_RATE_LIMITED`。
> 一轮 E2E 约消耗 20 次登录，连续重跑请间隔 60 秒以上。

未设置 `RUN_COVERAGE_E2E=true` 时该测试自动跳过，常规构建与 CI 不受影响。

## 通用网关骨架

通用网关骨架已独立到 `ddd-scaffold` 仓库的 `scaffold-gateway` 模块。骨架包含 Web 接入、Auth RPC 认证、参数校验、统一异常、
Actuator、Dubbo Triple 消费端和 Nacos 配置。认证契约来自共享制品 `ian-ddd-api-internal`（`cn.iantech.api.IAuthService`），
统一承载用户会话认证与渠道 HMAC 认证 RPC；两套认证算法仍分别由 Auth 与 Channel Cases 服务实现。
具体 RBAC 管理接口仍由业务网关自行接入，不复制到骨架中。

### 构建与安装

以下命令默认在 `ddd` 仓库根目录执行（两个仓库需同级检出）：

```bash
mvn -f ian-ddd-gateway/pom.xml clean verify
mvn -f ../ddd-scaffold/scaffold-gateway/pom.xml clean install
```

### 生成网关工程

```bash
mvn archetype:generate \
  -DarchetypeCatalog=local \
  -DarchetypeGroupId=cn.iantech \
  -DarchetypeArtifactId=scaffold-gateway \
  -DarchetypeVersion=1.0-SNAPSHOT \
  -DgroupId=cn.example \
  -DartifactId=demo-gateway \
  -DrootArtifactId=demo-gateway \
  -DuAppName=DemoGateway \
  -Dversion=1.0.0-SNAPSHOT \
  -Dpackage=cn.example.gateway \
  -DinteractiveMode=false
```

生成后执行：

```bash
cd demo-gateway
mvn clean package
```

接入业务 RPC 时，网关工程依赖共享契约 `ian-ddd-api-internal`（或目标服务自己的 `*-api` 制品），并在业务 Controller 中使用 `@DubboReference(protocol = "tri", retries = 0)` 调用。骨架不生成虚假的 RPC 接口或提供者。
