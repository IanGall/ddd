# Infrastructure 模块协作说明

## 模块定位
- 本模块实现领域与应用所需的外部能力适配。
- 包含持久化、缓存、消息、远程调用等技术实现。

## 变更边界
- 允许修改：Repository 实现、Mapper、Feign 客户端实现、配置装配。
- 禁止修改：领域规则定义、接口契约语义。

## 依赖约束
- 依赖 `domain`、实际使用的 `ddd-common` 公共类型与技术框架。
- Redis 访问统一依赖 `ddd-redis-starter` 提供的 `cn.iantech.redis.IRedisService`，业务 Infrastructure 不得重复定义 Redis
  服务或直接依赖 `RedissonClient`。
- 对上层通过接口暴露能力，避免技术细节泄漏。
- RBAC Repository、Mapper 和最终 UPDATE/DELETE SQL 必须显式接收并约束 `Long accountId`；禁止依赖前置查询实现隔离。
- 关系表按当前主账号物理删除，主表按当前主账号逻辑删除；跨主账号 ID、编码和父权限必须返回不存在或拒绝。

## 提交前检查
- 评估 SQL/远程调用次数，避免明显性能回退。
- 删除废弃适配器与未使用配置项。
