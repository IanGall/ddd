# 内部 API 聚合协作说明

## 模块定位
- 本目录是**内部 API 分类聚合**（`packaging=pom`）：服务之间通过 Dubbo（`dubbo` 协议）调用的 RPC 契约。
- 子模块命名 `<service>-api`，与 `<service>` 服务一一对应（当前 `ian-ddd-auth-api`）。
- 对外/第三方 HTTP 契约不属于本分类，放 `ian-ddd-api-external`。

## 变更边界
- 允许修改：`<modules>` 清单、各服务 API 子模块内的接口与传输 DTO。
- 禁止修改：把实现代码放进任何子模块；把外部 HTTP 契约混入本分类。

## 协作约束
- 新增服务契约：在此目录新建 `<service>-api`，parent 指向 `ian-ddd-api-internal`，并在本 pom 的 `<modules>` 登记。
- 新增子模块后必须在 `ddd-base-bom` 登记版本（消费方只写 artifactId）。
- 契约是跨进程协议：字段改名/删除属破坏性变更，需评估服务实现、网关与骨架消费方。

## 提交前检查
- `mvn -B -f ian-ddd-api/pom.xml verify` 通过（含各服务契约测试）。
- `<modules>` 与实际目录一致，无孤儿模块。
