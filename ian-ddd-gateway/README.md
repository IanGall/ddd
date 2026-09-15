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
`X-User-Id`。Dubbo 使用现有明文 RPC 连接，不启用 JWT、JWKS
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

# C 端注册：请求体只提交业务字段；客户端 IP 由网关按连接地址填充，不接受客户端提交
curl -X POST http://127.0.0.1:8092/api/app/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"loginName":"13800000000","password":"高强度密码","displayName":"C 端用户"}'
```

管理端业务接口固定使用 `/api/admin/**`，C 端业务接口固定使用 `/api/app/**`。两端请求均在 `Authorization` 请求头中携带
`Bearer <accessToken>`，Refresh Token 只提交给各自的 `/api/admin/auth/refresh` 或 `/api/app/auth/refresh`，不放入 URL
或业务请求头。注销和设备会话接口也分别位于两端的 `/auth/**` 子路径；C 端不建立逐用户 RBAC，最终授权由业务服务按可信
`customerId`、资源归属和有效绑定完成。

**管理端权限码获取（`GET /api/admin/auth/permissions`）**：返回当前主体的有效权限码数组（去重、升序），供管理端渲染菜单与
按钮。**主账号**返回其账号内**全部**权限码（含账号内自定义权限，**不过滤权限状态**），**子账号**返回角色聚合结果（停用或
软删除的用户/角色/权限会被排除）；每次调用实时读库，因此撤销角色/权限或停用账号、用户对下一次调用即时生效。

> 主账号刻意不过滤权限状态：`RbacAccessControlService#authorize` 对主账号是无条件放行、不看权限行状态，
> 若这里按状态过滤就会出现「接口能调通、菜单却不显示」的错位。客户端应**直接采用该接口返回的清单**作为唯一权限事实，
> 不要再自行按主体类型推断权限。

该端点**不要求任何权限码**——它是权限引导（bootstrap）端点，若要求调用者自身的权限会形成循环依赖，
没有 RBAC 读权限的子账号将无法加载自己的权限集合（表现为菜单全空）。主体取自认证过滤器校验后传播的可信上下文，
不需要在请求中携带额外参数。

**浏览器调用方的同源要求（网关侧事实）**：网关**没有任何 CORS 配置**——响应里不含 `Access-Control-*`，且预检 `OPTIONS`
会被认证白名单过滤器拦成 `401`（代码里没有 preflight 处理）。因此**浏览器跨域直连本网关一定失败**，使用方必须走同源路径：
开发期用前端开发服务器把 `/api` 代理到网关，生产期由集群入口在同一 Host 下按 `/` 与 `/api` 分流。
只有在确实需要跨域直连时才考虑加 CORS，且必须同时放行 `OPTIONS` 并明确允许的 origin 列表。
另外**静态资源不要挂到网关**：路径白名单只认 `/api/admin|app|external/**` 与 `/actuator/health`，前端产物应由独立 nginx 承载。

**Refresh Token 并发提交的语义（契约）**：刷新是**一次性轮换**——每次成功刷新都会作废旧 Refresh Token 并签发新的。
服务端把「旧 Refresh Token 再次出现」一律判定为**重放**，并撤销该设备的**整个会话族**（该账号在此设备上的 Access /
Refresh Token 全部失效，用户需重新登录）。因此**客户端必须串行刷新**：

- 同一 Refresh Token 不得并发提交，也不得在超时/失败后直接重试；
- 多标签页、冷启动、弱网重发等场景需在客户端串行化（同一时刻只允许一个刷新在途）；
- 刷新失败收到 `AUTH_REQUIRED` 属于**终态**，应清理本地令牌并重新登录，而不是重试。

并发提交被按重放处理是**有意的安全取舍**：服务端无法区分「同一客户端的重复提交」与「攻击者重放窃取到的令牌」，
而放行前者会削弱重放检测。该行为不可通过重试规避。

**C 端注册的入参边界**：`POST /api/app/auth/register` 只接受 `loginName`、`password`、`displayName`；客户端 IP 由网关按
`getRemoteAddr()` 填充后跨 RPC 传递给认证服务，用于注册入口的按 IP 风控。请求体中的同名字段会被忽略，客户端不得
依赖或伪造该维度。

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
| `NOT_FOUND`                           |         404 |
| `CONFLICT`                            |         409 |
| `PAYLOAD_TOO_LARGE`                   |         413 |
| `AUTH_RATE_LIMITED`                   |         429 |
| `RPC_ERROR`                           |         502 |
| `AUTH_UNAVAILABLE`、`RPC_NO_PROVIDER` |         503 |
| `RPC_TIMEOUT`                         |         504 |
| `INTERNAL_ERROR`                      |         500 |

> 上表与 `cn.iantech.common.constant.Constants.ResponseCode` 一一对应，该枚举是语义码的唯一来源。Auth 侧抛出的
> `AppException` 若携带枚举中**未登记**的码，网关按 `INTERNAL_ERROR`(500) 处理并记录「网关收到未登记的响应码」告警——
> 业务异常必须使用已登记的语义码，不存在「其他明确业务异常」这类兜底映射。
>
> 未匹配任何 API 分区的路径按形态区分语义：形态像 API 调用的（`/api/**`，如 `/api/v2/...`、`/api/unknown`）按
> `ACCESS_DENIED`(403) 处理，不披露路径是否存在；其余普通路径（`/favicon.ico`、已下线的旧路径等）按
> `NOT_FOUND`(404) 处理，避免把正常的「找不到」计入鉴权失败指标、误导调用方判断。形态非法的路径（百分号编码、
> 反斜杠、分号、重复斜杠、`.`/`..` 片段）一律按 `ACCESS_DENIED`(403)——它们本身就是攻击特征，不应报成 404。
>
> 本表由 `gateway-core` 的 `GatewayResponseCodeContractTest` 与枚举逐项比对（含增删与状态码一致性），
> 改动任一侧而未同步另一侧都会使构建失败。
>
> `AUTH_RATE_LIMITED` 覆盖两处按 IP 限流：登录入口 30 次/分钟/IP；C 端注册入口 10 次/分钟/IP（两者各自计数，互不占用额度）。

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

## 容器化部署（Kubernetes）

k8s 清单位于 `ian-ddd-gateway/dev-ops/k8s/`（原生 YAML：Deployment / Service / ConfigMap / Secret 示例 / HPA / PDB / Ingress），
操作步骤、凭证创建方式与已知限制见该目录的 `README.md`。

网关模块的镜像用模块自带的脚本构建（脚本会自动识别本机架构）：

```bash
bash ian-ddd-gateway/gateway-app/build.sh        # 自动跟随本机架构 → system/ian-ddd-gateway:1.0-SNAPSHOT

# 指定架构 / amd64 + arm64 双架构（多平台镜像无法 --load，必须推到镜像仓库）
PLATFORMS=linux/amd64 bash ian-ddd-gateway/gateway-app/build.sh
IMAGE=<可推送的仓库>/ian-ddd-gateway PLATFORMS=linux/amd64,linux/arm64 bash ian-ddd-gateway/gateway-app/build.sh
```

基础镜像是 `eclipse-temurin:21-jre`（官方 manifest list 自带 amd64/arm64），Dockerfile 无架构相关指令，
因此同一份 Dockerfile 即可出双架构镜像；混架构集群必须用最后一种推多架构镜像。

- 网关是唯一对外入口：Service 为 ClusterIP，对外经 `ingress.yaml`（`ingressClassName: apisix`，host 为占位）。
- Ingress 只做全量路径透传，**不复制路径白名单**：白名单的唯一真相在 `GatewayAuthFilter`，重复一份就会出现第二份真相。
- 探针用 `GET /actuator/health`（actuator 仅暴露 health），网关不持有数据源，health 为 UP 即代表真正可服务。
- 客户端 IP 目前取连接地址（`getRemoteAddr()`）；`server.forward-headers-strategy` 按既有结论不开启，原因与后续做法见
  `dev-ops/k8s/README.md` 的「客户端 IP」一节。

## 通用网关骨架

通用网关骨架已独立到 `ddd-scaffold` 仓库的 `scaffold-gateway` 模块。骨架包含 Web 接入、Auth RPC 认证、参数校验、统一异常、
Actuator、Dubbo 消费端和 Nacos 配置。认证契约来自共享制品 `ian-ddd-auth-api`（`cn.iantech.api.IAuthService`），
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

接入业务 RPC 时，网关工程依赖共享契约 `ian-ddd-auth-api`（或目标服务自己的 `*-api` 制品），并在业务 Controller 中使用 `@DubboReference(protocol = "dubbo", retries = 0)` 调用。骨架不生成虚假的 RPC 接口或提供者。
