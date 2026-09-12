# ddd-context 聚合模块协作说明

## 模块定位

- 本模块聚合协议无关上下文核心能力及 HTTP、Dubbo 边界适配器。
- `ddd-context-core` 定义统一上下文模型，`ddd-context-web` 和 `ddd-context-dubbo` 只负责协议转换。
- 本模块只作为 Maven 聚合入口，不产出运行时代码；业务模块必须按需依赖具体子模块。
- Maven 模块的 `<name>` 必须使用稳定的英文 `artifactId`，不得使用中文展示名。

## 架构边界

- Application 层允许依赖 `ddd-context-core`，通过 `ContextAccessor` 读取当前上下文，并把业务必需字段显式传入 Command
  或方法参数。
- Domain 层禁止依赖任何 `ddd-context-*` 模块，也禁止直接引用 ThreadLocal、Dubbo、Servlet 或 Spring。
- API DTO 不增加通用上下文字段，传输上下文不得与业务请求模型混合。
- Web 与 Dubbo 适配器可以依赖 Core；Core 禁止反向依赖任何适配器。
- 不增加旧实现兼容层，不使用 Dubbo 2 风格的 `RpcContext.getContext()`。

## 上下文安全

- 只允许传播 `requestId`、`principalName`、`tenantId`、`userId`、`grayTag`、`source`、`locale` 七个小型字符串字段。
- 统一使用 `x-ctx-*` Attachment 键；未知、空值、超长或非法字段必须忽略。
- 禁止传播 Authorization、密码、Cookie、权限列表、完整用户对象、业务 DTO 或任意 JSON。
- `tenantId`、`userId`、`grayTag` 等传入字段不得直接作为授权结论，权限仍由应用层校验。
- Web 自定义认证解析器只能把可信认证配置或 Claim 映射为上下文，禁止信任外部租户、用户身份 Header。

## 变更边界

- 修改公共字段、键名或校验规则时，必须同步检查 Core、Web、Dubbo 和对应测试。
- 新协议适配器应作为独立子模块接入，不得把协议依赖放入 Core。
- 只实现已明确需要的上下文能力，不提前引入 Kafka、OpenTelemetry 或后台任务传播。

## 测试与构建

- 测试方法名必须使用英文，中文场景说明使用代码注释；不得使用中文方法名。
- 覆盖作用域嵌套恢复、异常清理、线程隔离、字段校验和协议边界传播。
- 修改后在本目录执行 `mvn -q test`，并在 `ddd-base` 根目录执行 `mvn -q install`。
- 提交前执行 `git diff --check`，清理无用代码、失效配置和重复实现。
