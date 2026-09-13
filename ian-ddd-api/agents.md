# API 契约聚合工程协作说明

## 模块定位
- 本目录是 **API 契约聚合工程**（`packaging=pom`），按「内部 / 外部」分类、再按服务拆子模块：
  - `ian-ddd-api-internal`：内部 API 分类聚合，下挂 `<service>-api`（当前 `ian-ddd-auth-api`），服务间 **Dubbo RPC** 契约（`cn.iantech.api.*`）。
  - `ian-ddd-api-external`：对外/第三方 **HTTP** 契约（`cn.iantech.api.external.*`，Spring 声明式 `@HttpExchange`）。
- 契约独立于任何服务实现；服务实现、网关与骨架消费这些契约，不各自复制。

## 变更边界
- 允许修改：聚合 POM 的模块清单与依赖管理、两个子模块内的接口与传输模型。
- 禁止修改：把实现代码（domain/infrastructure/cases/具体服务）放进任何契约模块。
- 内部/外部不可混放：Dubbo 服务契约只进 `-internal`；HTTP 外部契约只进 `-external`。

## 协作约束
- 新增子模块后必须在 `ddd-base-bom` 登记版本，并同步消费方（服务实现、网关、骨架模板）。
- 契约是跨进程协议：字段改名/删除属于破坏性变更，需评估所有消费方。
- `-external` 不得引入 Spring Cloud OpenFeign 或其它 HTTP 客户端；`spring-web` 已足够。

## 提交前检查
- `mvn -B -f ian-ddd-api/pom.xml verify` 通过（各子模块契约测试）。
- 聚合 POM 的 `<modules>` 与实际目录一致，无孤儿模块。
