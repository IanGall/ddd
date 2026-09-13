## 变更说明

<!-- 做了什么、为什么 -->

## 影响面

- [ ] 契约变更（HTTP / Dubbo）——若勾选，需同步更新对应 README 与 `docs/plans/project-optimization-plan.md` 的契约章节
- [ ] 数据库结构变更——需附一次性迁移脚本（`docs/dev-ops/environment/sql/`）
- [ ] 配置 / 凭据变更——敏感值必须走环境变量，且不保留可用默认值
- [ ] 脚手架模板变更——需同步参考实现，避免生成工程漂移

## 验证

- [ ] `mvn -B -f ddd/pom.xml install -Pcoverage-gate`
- [ ] `mvn -B -f ddd-scaffold/pom.xml clean verify`
- [ ] 其他（E2E / 手工验证）：

## 备注
