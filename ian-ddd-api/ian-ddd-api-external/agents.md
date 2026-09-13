# ian-ddd-api-external 协作说明

## 模块定位
- 描述**对外/第三方服务**的 HTTP 调用契约，与内部 Dubbo RPC 契约（`ian-ddd-api-internal` 下的 `ian-ddd-auth-api`）分开。
- 使用 Spring 声明式 HTTP 接口：`@HttpExchange` + `@GetExchange`/`@PostExchange`…，由 `HttpServiceProxyFactory` + `RestClient` 生成代理。

## 变更边界
- 允许：新增/修改外部服务接口与其请求/响应模型（放在对应 `model` 子包）。
- 禁止：引入 Spring Cloud OpenFeign 或其它 HTTP 客户端框架；禁止放入内部 Dubbo 服务契约；禁止引入实现依赖。

## 依赖约束
- 仅依赖 `spring-web`（提供 `@HttpExchange`、`HttpServiceProxyFactory`、`RestClient`）与 `ddd-common`、Lombok。
- **零额外第三方 HTTP 客户端依赖**：不引 Spring Cloud，不引 feign-core。
- 需要超时/认证/拦截器时，调用方自建 `RestClient` 后用 `ExternalApiClientFactory.create(type, restClient)`。

## 约定
- 每个外部服务一个接口，类级 `@HttpExchange` 声明基础路径与 `accept`；方法级注解声明具体调用。
- 响应模型优先用不可变 `record`；跨服务字段保持向后兼容。

## 提交前检查
- 契约接口都标注 `@HttpExchange`（`ExternalApiClientFactoryTest` 会校验示例接口）。
- `mvn -B -f ian-ddd-api/pom.xml verify` 通过。
