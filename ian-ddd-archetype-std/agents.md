# ian-ddd-archetype-std 协作说明

## 模块定位
- 本目录是标准 DDD 脚手架总工程，管理模板聚合与分层模块协同。
- 负责统一版本、构建约束与模板输出一致性。

## 变更边界
- 允许修改：父工程构建配置、模块依赖关系、脚手架总装配逻辑。
- 禁止修改：与当前需求无关的业务实现代码。

## 协作约束

- 分层职责清晰：`api/boot/domain/infrastructure/trigger` 各司其职，公共基础能力统一复用 `ddd-common`。
- RBAC 数据按主账号 `account_id` 隔离；Cases 完成主账号/子账号权限授权，Infrastructure 的每条 SQL 都必须带账号条件。
- 旧租户表和管理员专用认证不保留兼容层，模板生成结果必须直接采用主账号/子账号模型。
- 删除无用配置与废弃文件，避免冗余。

## 提交前检查
- 验证模块结构完整，关键模板目录可用。
- 确认变更不引入循环依赖或明显构建风险。
- 检查 Domain 未引入 Cases、上下文、Servlet、Dubbo 或基础设施实现；Spring `@Service` 仅允许用于领域服务实现和 `cases` 服务。
- Domain 对基础设施的能力契约统一放在 `domain.<业务域>.infra`，不得保留 `port`、`repository` 或 `adapter` 契约包。
