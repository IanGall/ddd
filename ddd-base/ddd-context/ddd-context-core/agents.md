# ddd-context-core 模块协作说明

## 模块定位

- 本模块提供纯 Java、协议无关的请求上下文模型和作用域管理能力。
- 公共 API 包括 `RequestContext`、`ContextAccessor`、`ContextScope`、`ContextSnapshot`、`ContextKeys` 和 `ContextValidator`。
- Application 层可以直接依赖本模块；Domain 层不得依赖本模块。

## 依赖约束

- 主代码禁止引入 Spring、Dubbo、Servlet、Kafka 或其他协议和框架依赖。
- 使用普通 `ThreadLocal` 保存当前上下文，禁止改为 `InheritableThreadLocal`。
- 线程上下文访问必须通过 `ContextAccessor`，不得向调用方暴露 ThreadLocal。
- `RequestContext` 必须保持不可变，只承载少量字符串标量，不得加入业务对象、集合或认证凭证。

## 作用域规则

- 安装上下文必须使用 `ContextAccessor.open(...)` 和 try-with-resources 关闭 `ContextScope`。
- `ContextScope` 必须支持嵌套恢复，并保证重复关闭安全。
- 异步任务只能使用显式捕获的 `ContextSnapshot` 包装，不得依赖线程继承或隐式共享。
- 空上下文和异常路径必须清理 ThreadLocal，禁止线程池复用造成上下文串线。

## 字段与校验

- 字段固定为 `requestId`、`principalName`、`tenantId`、`userId`、`grayTag`、`source`、`locale`。
- 普通字段最大长度为 128，`locale` 最大长度为 32，只接受允许的安全字符。
- 统一键名和校验逻辑必须集中维护，禁止适配器复制另一套常量或校验规则。
- 新增或删除字段属于跨模块协议变更，必须同步所有适配器及测试，不保留旧字段兼容逻辑。

## 测试与构建

- 测试方法名必须使用英文，中文场景说明使用代码注释；不得使用中文方法名。
- 必须覆盖空上下文、嵌套恢复、异常清理、重复关闭、并发线程隔离和快照包装。
- 修改后执行 `mvn -q -pl ddd-context-core test`；跨模块字段变更还需在 `ddd-base` 根目录执行 `mvn -q install`。
- 删除未使用的公共 API、常量和重复校验代码，不保留废弃入口。
