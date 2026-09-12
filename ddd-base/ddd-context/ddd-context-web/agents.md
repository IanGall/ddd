# ddd-context-web 模块协作说明

## 模块定位

- 本模块在 Spring Web 请求边界建立协议无关的 `RequestContext`。
- `ContextWebFilter` 负责读取请求信息、安装上下文、回写请求标识并在请求结束后恢复父上下文。
- 本模块只适用于 Gateway 或显式启用 HTTP Profile 的标准服务，Application 层应只依赖 Core。

## HTTP 与认证约束

- `ContextWebFilter` 必须在 Sa-Token 认证过滤器之后由 Servlet 容器执行。
- 自动配置统一注册 Filter，并设置最低优先级，避免在认证前执行或同一请求执行两次。
- `requestId` 读取并校验 `X-Request-Id`，缺失或非法时生成 UUID，并回写同名响应头。
- `principalName` 只能读取已认证的 Sa-Token 登录 ID，禁止信任外部身份 Header。
- 默认解析器只提供 `principalName`，`tenantId` 和 `userId` 保持为空。
- 应用可注入 `AuthenticationContextResolver`，从已校验的本地认证配置或可信 Claim 补充租户；禁止从外部身份 Header 补充。
- 根 HTTP 上下文的 `source` 固定为 `gateway`；匿名和健康检查请求也必须建立请求上下文。

## 安全与清理

- `ContextWebFilter` 禁止直接读取或传播外部 `X-User-Id`、`X-Tenant-Id`、Authorization、Cookie、密码、权限列表和业务对象；Sa-Token
  认证入口可以读取 Authorization Bearer/Token 完成认证，但凭证原文不得写入 `RequestContext`、日志或 Dubbo Attachment。
- 自定义解析器必须校验已认证主体与本地配置的绑定关系，不能只依据请求参数决定租户。
- `grayTag`、`locale` 只有在明确来源白名单和格式校验完成后才允许接入，且不得作为授权依据。
- 整个过滤链必须由 `ContextScope` 包裹，正常返回或异常时都要恢复进入请求前的上下文。
- 不在 Controller 中手工设置 Dubbo Attachment，RPC 传播由 `ddd-context-dubbo` 负责。

## 测试与构建

- 测试方法名必须使用英文，中文场景说明使用代码注释；不得使用中文方法名。
- 必须覆盖 Sa-Token 登录 ID 提取、外部身份 Header 忽略、请求 ID 生成与回写、匿名请求和请求间隔离。
- 修改后执行 `mvn -q -pl ddd-context-web test`；涉及 Core 协议时执行 `mvn -q install`。
- 自动配置或过滤器顺序变更必须增加对应 Sa-Token 集成测试。
