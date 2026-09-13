# Domain 模块协作说明

## 模块定位

- 本模块承载 Cases 用例编排、核心领域模型、领域服务与业务规则。
- 聚焦业务一致性与模型表达能力。

## 变更边界

- 允许修改：Cases、聚合、实体、值对象、领域服务、领域事件。
- 禁止修改：接口协议定义、数据库映射、第三方客户端调用。

## 依赖约束

- 核心领域包仅依赖领域规则所需的 `ddd-common` 公共类型或最小公共抽象。
- `cn.iantech.cases` 可使用 Spring Service、事务和日志编排多个基础设施契约；`cn.iantech.domain` 下的领域服务实现可使用
  Spring `@Service` 自动注册。
- `domain.<业务域>.infra` 统一定义由 Infrastructure 实现的技术无关契约；核心模型、聚合、实体、值对象及 infra 接口不得依赖
  Spring、MyBatis、Redis、Dubbo、Servlet 或 Infrastructure 实现。
- Trigger 将主账号和操作者解析为显式 Actor 传入 Cases；Domain 禁止依赖 API DTO、`ddd-context-*`
  、ContextAccessor、Servlet、Dubbo 或 Infrastructure 实现。

## 提交前检查
- 关键规则需覆盖正常路径与边界条件。
- 删除无效规则分支与失效模型字段。
