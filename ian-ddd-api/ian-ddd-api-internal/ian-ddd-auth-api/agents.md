# ian-ddd-auth-api 协作说明

## 模块定位
- 定义**认证服务 `ian-ddd-auth`** 与网关之间的 Dubbo RPC 契约（Auth / RBAC / Customer / Channel 接口 + 请求/响应 DTO）。
- 位于「内部 API」分类聚合 `ian-ddd-api-internal` 之下；命名规则为 `<service>-api`。
- 单一来源、独立制品：服务实现、网关、骨架生成的网关都依赖本模块，不再各自复制契约。
- 对外/第三方 HTTP 契约不放这里，放 `ian-ddd-api-external`。

## 变更边界
- 允许修改：`cn.iantech.api` 下的接口定义与 `cn.iantech.api.model.*` DTO、字段校验约束。
- 禁止修改：任何应用编排、领域规则、持久化细节；禁止引入实现依赖（domain/infrastructure/cases/具体服务）。

## 依赖约束
- 仅依赖 `ddd-common` 的公共类型（如分页模型）与 Lombok。
- DTO 必须实现 `Serializable` 且提供无参构造；不得包含密码哈希、渠道密钥等敏感字段。
- 版本由 `ddd-base-bom` 统一管理，消费方只写 `artifactId`。

## 提交前检查
- `ApiContractTest` 必须通过：方法签名不得越出 `cn.iantech.api`/JDK 边界，契约类型可反序列化。
- 字段命名与含义保持向后兼容；删除未使用的契约字段与无效注释。
- 改契约后回归：`mvn -B -f ddd/pom.xml install -Pcoverage-gate` → `mvn -B -f ddd-scaffold/pom.xml clean verify`。
