# ian-ddd-api — API 契约聚合工程

服务对外与对内的 **API 契约**聚合工程，按「内部 / 外部」分类、再按服务拆子模块，独立于任何服务实现。

```text
ian-ddd-api/                          # 聚合 POM（packaging=pom）
├── ian-ddd-api-internal/             # 内部 API 分类聚合（packaging=pom）
│   └── ian-ddd-auth-api/             # 认证服务的 Dubbo RPC 契约
│       └── cn.iantech.api
│           ├── IAuthService              # 登录 / 刷新 / 校验 / 注销 / 会话
│           ├── IRbacService              # RBAC 用户 / 角色 / 权限 / 关系
│           ├── ICustomerService          # C 端用户注册与认证
│           ├── IChannelCredentialService # 渠道凭证管理
│           ├── IPlatformAccountService   # 平台开户（X-Platform-Token）
│           └── model.{auth,rbac,channel,customer}   # 请求/响应 DTO
└── ian-ddd-api-external/             # 外部 API：对外/第三方 HTTP 契约（声明式 @HttpExchange）
    └── cn.iantech.api.external
        ├── sample                    # 骨架示例（可删）
        └── support                   # ExternalApiClientFactory（RestClient 生成代理）
```

## 内部 vs 外部

| 维度 | `ian-ddd-api-internal`（分类聚合） | `ian-ddd-api-external` |
|------|-----------------------------------|------------------------|
| 子模块 | `<service>-api`，当前 `ian-ddd-auth-api` | 直接承载外部 HTTP 契约 |
| 调用方式 | Dubbo Triple（RPC） | HTTP，Spring 声明式接口 `@HttpExchange` |
| 包名 | `cn.iantech.api.*` | `cn.iantech.api.external.*` |
| 额外依赖 | 无 | `spring-web`（无 Spring Cloud / Feign） |
| 消费方 | 服务实现（Provider）、网关（Consumer） | 需要调用外部/第三方 HTTP 服务的模块 |

> 内部契约的包名保持 `cn.iantech.api.*` 不变（消费方 import 零改动）；外部契约为命名空间 `cn.iantech.api.external.*`。

## 坐标与版本

| 坐标 | 说明 |
|------|------|
| `cn.iantech:ian-ddd-api` | 聚合 POM（不产出 jar） |
| `cn.iantech:ian-ddd-api-internal` | 内部 API 分类聚合 POM（不产出 jar） |
| `cn.iantech:ian-ddd-auth-api` | 认证服务的内部 Dubbo RPC 契约 jar |
| `cn.iantech:ian-ddd-api-external` | 外部 HTTP 契约 jar |

- 版本统一 `1.0-SNAPSHOT`，由 `ddd-base-bom` 管理，消费方只写 `artifactId`。
- 父 POM 为根聚合 `ddd`（**不挂 `ddd-base`**），避免继承 ddd-base 的 60% 覆盖率门槛——契约是 DTO/接口，行覆盖率天然极低。

## 新增契约

**内部（Dubbo）**：在 `ian-ddd-api-internal` 下按服务新建 `<service>-api` 子模块（包 `cn.iantech.api.*`），DTO 必须 `Serializable` + 无参构造。

**外部（HTTP）**：在 `ian-ddd-api-external` 的 `cn.iantech.api.external.<service>` 下新增接口，类级标注 `@HttpExchange`；调用方用 `ExternalApiClientFactory.create(Type.class, baseUrl)` 生成代理。

新增子模块时：在对应父 POM 的 `<modules>` 登记，并在 `ddd-base-bom` 增加版本管理。

## 边界约定

- **只放契约**：接口与传输模型，不引入任何实现依赖。
- **不泄漏实现**：DTO 不得包含密码哈希、渠道密钥等敏感字段。
- **可反序列化**：内部契约类型必须可被标准 JSON/二进制反序列化；`ApiContractTest` 会在构建时校验签名边界与无参构造。
- 详细规则见 [ian-ddd-api-internal/agents.md](ian-ddd-api-internal/agents.md)、[ian-ddd-auth-api/agents.md](ian-ddd-api-internal/ian-ddd-auth-api/agents.md) 与 [ian-ddd-api-external/agents.md](ian-ddd-api-external/agents.md)。

## 构建

```bash
# API 聚合（含全部子模块）
mvn -B -f ian-ddd-api/pom.xml verify

# 仓库根一次构建（推荐）
mvn -B -f pom.xml install -Pcoverage-gate
```
