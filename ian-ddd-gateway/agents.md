# ian-ddd-gateway 模块协作说明

## 模块定位
- 本仓库是网关参考应用与 Maven Archetype 的聚合工程。
- `gateway-app` 承载现有 RBAC 网关，`scaffold-gateway` 负责生成通用网关工程。

## 变更边界
- 参考应用负责 HTTP 接入、参数校验、协议适配与下游调用编排。
- 通用骨架绑定标准工程 `IAuthService` 的认证契约和 `IRbacService` 的平台开户契约，不复制具体 RBAC 管理接口，也不得生成虚假的
  RPC 契约。
- 核心领域规则必须由下游 DDD 服务实现。
- 网关每个受保护请求通过 Auth RPC 校验 opaque Token 后建立主账号与当前用户上下文，不信任 `X-Account-Id`、`X-User-Id` 等外部身份
  Header。
- Dubbo Triple 消费端使用明文 RPC；网关不持有 Session、不连接 Auth Redis，不使用 JWT 或 Sa-Token 本地认证。

## 依赖约束
- 构建统一继承 `ddd-base`，通用依赖统一导入 `ddd-base-bom`，避免模块内分散定义版本。
- 参考应用只管理实际使用的标准 API 契约，不导入标准框架全量 BOM。
- 通过标准 API 契约与公共能力协作，避免直接耦合下游实现细节。
- 公共模型复用 `ddd-common`，禁止新增重复 `types/common` 模块或 Hutool 依赖。

## 提交前检查
- 校验入口参数与返回语义一致，异常路径可观测可追踪。
- 评估远程调用次数，避免出现明显 N+1 或重复调用问题。
- 删除未使用的控制器代码、无效配置与过期注释。
- 执行根聚合工程 `mvn clean verify`，确保参考应用和骨架生成工程同时通过。
