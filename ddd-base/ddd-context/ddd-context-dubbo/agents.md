# ddd-context-dubbo 模块协作说明

## 模块定位

- 本模块负责 `RequestContext` 与 Dubbo 3 Attachment 之间的双向转换。
- Consumer Filter 写入 Client Attachment，Provider Filter 从 Server Attachment 建立调用作用域。
- 业务代码不得感知 Filter 或 `RpcContext`，Application 与 Domain 均不得依赖本模块。

## Dubbo 约束

- 只允许使用 `RpcContext.getClientAttachment()` 和 `RpcContext.getServerAttachment()`。
- 禁止使用 Dubbo 2 风格的 `RpcContext.getContext()`，也不提供兼容层。
- Filter 必须通过 Dubbo SPI 文件和 `@Activate` 自动启用，不要求 Controller 或业务服务手工设置附件。
- 不修改 RPC DTO，不改变现有 Triple、Nacos、超时或重试策略。

## 传播与清理

- Consumer 只写入 `DubboContextAttachments` 定义的白名单字段。
- Consumer 调用结束后必须在 `finally` 中恢复调用前附件，保证嵌套调用和线程复用安全。
- Provider 必须校验 Server Attachment 后构造新的 `RequestContext`，并使用 `ContextScope` 包裹整个调用。
- Provider 在正常返回、业务异常或 RPC 异常时都必须恢复父上下文。
- 未知、空值、超长或非法附件必须忽略，禁止透传任意 Attachment。

## 安全边界

- 禁止传播 Authorization、Cookie、密码、权限集合、完整用户对象、业务 DTO 或 JSON。
- `tenantId`、`userId`、`principalName`、`grayTag` 仅是调用上下文，不得直接作为授权结论。
- Attachment 键名和字段校验必须复用 `ddd-context-core`，不得在本模块另建不一致规则。

## 测试与构建

- 测试方法名必须使用英文，中文场景说明使用代码注释；不得使用中文方法名。
- 必须覆盖白名单写入、非法字段忽略、附件恢复、Provider 异常清理和 A → B → C 嵌套调用。
- 修改后执行 `mvn -q -pl ddd-context-dubbo test`；涉及 Core 协议时执行 `mvn -q install`。
- 修改 SPI 配置后必须验证 Filter 能被 Dubbo 扩展机制发现。
