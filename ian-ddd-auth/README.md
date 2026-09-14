# ian-frame-archetype - DDD 脚手架

## 重要信息

- 工程使用与 `domain` 同级的 `cases` 包承载用例编排与事务边界；`boot` 模块仅作为 Spring Boot 启动装配根。
- 单实现的 Case Service 直接使用具体类，只有存在多实现或明确替换边界时才增加接口；Repository、Domain Port 和框架
  Mapper 接口继续承担依赖倒置与适配职责。
- `xxx` 是占位业务域示例，`user` 是包含演示用例的业务域样例，避免多个占位域重复表达相同分层。
- 框架使用文档：[https://iantech.cn/md/road-map/ddd-archetype.html](https://iantech.cn/md/road-map/ddd-archetype.html) - 此文章下还有系列的 DDD 知识

## 默认能力说明

- 标准骨架默认不包含 HTTP 触发能力，不再内置 Spring MVC 依赖与示例 Controller。
- 如需提供 HTTP 接口，可在工程根目录构建时启用 `-Phttp`，为 `*-trigger` 模块按需引入 `spring-boot-starter-web`。
- 启用 `-Phttp` 后，按需在 `*-trigger` 模块新增 Controller 实现即可。

## 全局唯一 ID

- Infrastructure 默认依赖 `ddd-id-generator-starter`，业务通过构造器注入
  `cn.iantech.id.GlobalIdGenerator`，调用 `nextId()` 获取 `long` 类型全局唯一 ID。
- Starter 使用 Redis 租约自动分配并续租 Worker ID；应用无需手工配置 Worker ID，但必须提供可用的 Redis 连接。
- 主账号、管理子账号、C 端用户、渠道凭证、渠道数据范围及 Auth Session/Family ID 均由该 Starter 生成；对应数据库主键不再使用
  `AUTO_INCREMENT`。Access Token、Refresh Token、渠道密钥和 AES IV 仍使用不可预测的安全随机值。
- `test` Profile 默认设置 `ddd.id-generator.enabled=false`，避免普通测试连接外部 Redis；需要验证 ID 租约时，应使用独立集成测试
  Profile 和隔离的 Redis 实例。

## 上下文与服务认证

- Trigger 负责解析可信请求上下文；HTTP 与 Dubbo 适配器分别按 Profile/模块引入 `ddd-context-web`、
  `ddd-context-dubbo`，并将显式 Actor 传给 Cases。
- RBAC 主账号 ID 由可信认证上下文提供，不能使用 `X-Account-Id` 或 `X-User-Id` 请求头；领域层只接收显式 `Long accountId`。
- Provider 使用明文 Triple RPC；Nacos 注册中心仍通过用户名和密码认证，生产凭据由部署环境注入。

## Auth、RBAC 与 Customer 安全边界

- Auth 是 opaque Access Token、Refresh Token、登录风控和设备会话的唯一所有者，Gateway 不访问 Auth Redis。
- RBAC 与 Customer 只向 Auth 提供身份校验和主体状态重载：RBAC 负责主账号、子账号及角色权限，Customer 负责 C 端用户；两者不持有
  Token 或会话。
- 渠道 HMAC 继续由 Channel Case Service 负责密钥版本、时间窗口和防重放，不进入用户会话；它与用户认证只共享
  `IAuthService` Dubbo 入口。
- Token 不是 JWT；客户端必须始终将其作为 opaque Token 使用。Auth 签发的 `v4.<userId>.<随机密钥>`
  仅携带 Redis Cluster 路由信息，路由段不是可信身份；只有完整 Token 摘要命中且 Session 主体一致时才认证成功。
- 会话数据使用 `auth:session:v4:{<userId>}:*` 命名空间。用户 ID 由全局 ID Starter 统一生成，同一用户的 Session、Access、
  Refresh、Family 与用户索引固定落在同一 Redis Cluster slot。旧 v3 Token 和 Session 不迁移、不回退，升级后客户端必须重新登录。
- Refresh Token 轮换、重放检测、Family 撤销、登录风控、C 端注册入口的按 IP 风控和设备会话上限均由 Auth 在 Redis 中执行。
  登录与注册各自计数：登录 30 次/分钟/IP，注册 10 次/分钟/IP；两者只使用不可逆摘要，原始 IP 不落 Redis。
- Gateway HTTP 路径只使用 `/api/admin/**`、`/api/app/**`、`/api/external/**` 三类前缀，不保留旧路径。
- 管理端和 C 端分别使用 `/api/admin/auth/**`、`/api/app/auth/**`；刷新请求必须通过 `expectedSubjectType`
  声明 `ADMIN` 或 `CUSTOMER`，Auth 在轮换前校验 Session 主体。
- `/api/external/**` 使用渠道 HMAC，认证身份的授权范围固定为 `external:access`。渠道协议的取值统一收敛在
  `Constants`（`ChannelAuth` 的时钟偏移窗口与防重放 TTL、`AuthScope.EXTERNAL_ACCESS`、`TokenKind.CHANNEL_HMAC`），
  面向对接方的协议说明与示例见 `ian-ddd-gateway/README.md` 的「渠道 HMAC 请求」章节——此处刻意不重复具体数值，
  避免同一参数在文档中出现第二份副本。
- `rbac:*` 为系统权限前缀，系统权限只能由部署 SQL 和主账号初始化流程创建，运行时禁止更新、禁用或删除。
- `POST /api/admin/platform/accounts` 的 `X-Platform-Token` 由 Provider 最终校验，Gateway 只负责转发。
- Dubbo 身份上下文仅依赖私网、注册中心权限和网络白名单；`source=gateway` 不具备密码学防伪能力，Dubbo 端口禁止暴露到公网。
  注册入口的客户端 IP（`CustomerRegisterReq.ipAddress`）由 Gateway 按连接地址填充，同样只具备该私网信任前提：
  它用于风控分桶，不作为身份凭据。

## 本地启动

本地开发凭证统一放在仓库根 `.env.local`（已被 `.gitignore` 忽略），模板见仓库根 `.env.example`：

```bash
# 首次准备（在仓库根目录执行）
cp .env.example .env.local
# 编辑 .env.local：至少填写 MYSQL_PASSWORD / REDIS_PASSWORD / DUBBO_REGISTRY_PASSWORD /
# CHANNEL_ENCRYPTION_MASTER_KEY / PLATFORM_ADMIN_TOKEN

# 启动：docs/dev-ops/start-with-coverage.sh 与容器脚本会自动加载 .env.local
mvn -q spring-boot:run -pl ian-ddd-auth-boot -Pdev
```

- 数据库账号/口令通过 ShardingSphere 占位符 `$${MYSQL_USERNAME::root}` / `$${MYSQL_PASSWORD::}` 注入
  （JDBC URL 带 `?placeholder-type=environment`）；Redis、Nacos、渠道主密钥、平台令牌通过 Spring
  `${ENV}` 占位符注入，配置文件中不保留任何可用默认值。
- IntelliJ 直跑时请在 Run/Debug Configuration 的 Environment variables 中导入 `.env.local` 内容（或选择
  EnvFile 插件）；启动脚本场景无需手工导出。
- 调用本地平台开户接口时使用 `X-Platform-Token`（值取自 `.env.local` 的 `PLATFORM_ADMIN_TOKEN`）。
- 生产环境不使用 `.env.local`：由部署平台注入同名环境变量，`SPRING_PROFILES_ACTIVE=prod` 已由 Dockerfile 固定；
  非本地 Profile 使用示例令牌或全零渠道密钥会被启动校验（`SecretConfigurationValidator`）直接拒绝。

## 覆盖率

本工程有两个覆盖率数字，口径不同、互不可替代：

- **单服务覆盖率**（`mvn verify -Pcoverage-gate`）：本工程各模块单元测试对 `src/main` 的覆盖，不需要外部依赖。
  当前门槛：`domain` 35%、`infrastructure` 15%；`trigger` / `api` 尚无测试、暂无门槛。
- **分布式 E2E 覆盖率**：由 Gateway 发起的真实跨服务调用链，覆盖本服务的 trigger / domain / infrastructure / api。
  需要本服务以 Agent 模式启动（`docs/dev-ops/start-with-coverage.sh`，Agent 端口 6301）并配合覆盖率控制器。

完整对照、门槛配置与一键流水线见 `ddd-base/ian-ddd-coverage/README.md`。

## Maven - 阿里云镜像

```java
  <!-- mirrors
   | This is a list of mirrors to be used in downloading artifacts from remote repositories.
   |
   | It works like this: a POM may declare a repository to use in resolving certain artifacts.
   | However, this repository may have problems with heavy traffic at times, so people have mirrored
   | it to several places.
   |
   | That repository definition will have a unique id, so we can create a mirror reference for that
   | repository, to be used as an alternate download site. The mirror site will be the preferred
   | server for that repository.
   |-->
  <mirrors>
    <!-- mirror
     | Specifies a repository mirror site to use instead of a given repository. The repository that
     | this mirror serves has an ID that matches the mirrorOf element of this mirror. IDs are used
     | for inheritance and direct lookup purposes, and must be unique across the set of mirrors.
     | 
    <mirror>
        <id>alimaven</id>
        <mirrorOf>central</mirrorOf>
        <name>aliyun maven</name>
        <url>https://maven.aliyun.com/nexus/content/repositories/central/</url>
    </mirror>

  </mirrors>
```

- 没有镜像你可能会出现拉取很慢的问题，以及错误。
