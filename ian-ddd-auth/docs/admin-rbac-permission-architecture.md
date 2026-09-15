# Admin RBAC 权限架构

## 权限边界

Admin 请求采用三层权限边界：

1. Gateway 只校验 opaque Token、可信身份和 `ADMIN_PRIMARY` / `ADMIN_SUB_ACCOUNT` 主体分区。
2. Cases 在用例入口按 `RbacPermissionCode` 执行权限校验并记录授权审计（`GET /api/admin/auth/permissions`
   是唯一的权限引导例外，见「接口权限矩阵」下方说明）。
3. Domain 和 Repository 执行对象级委派规则，所有用户、角色、权限及关系操作必须携带 `accountId`。

Gateway 不持有权限快照，也不是最终授权边界。Auth 是 Token、会话和登录风控的唯一所有者；RBAC 只是管理员身份校验与权限状态
提供方，Customer 则独立提供 C 端身份校验。数据库中的有效用户、角色、权限和关系是唯一权限事实来源；Auth 校验 Token 时通过对应
身份端口重新加载主体状态，业务用例每次从数据库校验权限，因此停用账号、停用用户、撤销角色或权限关系会对后续请求即时生效。

## 接口权限矩阵

| Admin 接口                               | 方法   | 权限码                       | 对象级约束                         |
|------------------------------------------|--------|------------------------------|------------------------------------|
| `/api/admin/rbac/users`                  | GET    | `rbac:user:read`             | 仅当前 `accountId`                 |
| `/api/admin/rbac/users/{id}`             | GET    | `rbac:user:read`             | 仅当前 `accountId`                 |
| `/api/admin/rbac/users`                  | POST   | `rbac:user:create`           | 仅当前 `accountId`                 |
| `/api/admin/rbac/users/{id}`             | PUT    | `rbac:user:update`           | 禁止跨账号对象                     |
| `/api/admin/rbac/users/{id}`             | DELETE | `rbac:user:delete`           | 禁止跨账号对象                     |
| `/api/admin/rbac/roles`                  | GET    | `rbac:role:read`             | 仅当前 `accountId`                 |
| `/api/admin/rbac/roles/{id}`             | GET    | `rbac:role:read`             | 仅当前 `accountId`                 |
| `/api/admin/rbac/roles`                  | POST   | `rbac:role:create`           | 仅当前 `accountId`                 |
| `/api/admin/rbac/roles/{id}`             | PUT    | `rbac:role:update`           | 禁止跨账号对象                     |
| `/api/admin/rbac/roles/{id}`             | DELETE | `rbac:role:delete`           | 禁止跨账号对象                     |
| `/api/admin/rbac/permissions`            | GET    | `rbac:permission:read`       | 仅当前 `accountId`                 |
| `/api/admin/rbac/permissions/{id}`       | GET    | `rbac:permission:read`       | 仅当前 `accountId`                 |
| `/api/admin/rbac/permissions`            | POST   | `rbac:permission:create`     | 自定义权限不得使用 `rbac:` 前缀    |
| `/api/admin/rbac/permissions/{id}`       | PUT    | `rbac:permission:update`     | 子账号仅能管理自己拥有的自定义权限 |
| `/api/admin/rbac/permissions/{id}`       | DELETE | `rbac:permission:delete`     | 系统托管权限不可删除               |
| `/api/admin/rbac/users/{id}/roles`       | GET    | `rbac:user-role:read`        | 用户和角色必须属于同一账号         |
| `/api/admin/rbac/users/{id}/roles`       | PUT    | `rbac:user-role:grant`       | 子账号不得授予自身未拥有的权限集合 |
| `/api/admin/rbac/roles/{id}/permissions` | GET    | `rbac:role-permission:read`  | 角色和权限必须属于同一账号         |
| `/api/admin/rbac/roles/{id}/permissions` | PUT    | `rbac:role-permission:grant` | 子账号不得授予自身未拥有的权限     |
| `/api/admin/auth/permissions`            | GET    | **无（权限引导端点，见下）** | 仅当前主体自己的权限码             |

主账号拥有账号内全部权限，但账号必须仍处于有效状态。权限不足、跨账号对象和对象不存在统一返回拒绝，不向调用方泄露其他账号对象是否存在。

### 权限引导端点（`GET /api/admin/auth/permissions`）

`/api/admin/auth/permissions` 返回当前主体的有效权限码（去重、升序），是上表**唯一的无权限码例外**：

- **为什么不要权限码**：它本身就是客户端获取权限的入口。若要求调用者持有某个权限码，就会形成循环依赖——
  子账号必须先有权限才能查到自己的权限；现实中子账号通常只被授予业务权限，结果是前端菜单全空。
- **它仍然安全**：主体取自网关认证过滤器校验后传播的可信上下文（不采信外部 Header），只能返回**调用者自己**的权限码，
  无法查询他人；且每次调用实时读库，撤销关系、停用账号/用户即时生效。
- **主账号语义**：与 `RbacAccessControlService#authorize` 的放行语义**严格一致**——主账号拥有账号内全部权限，
  且 `authorize` 对主账号是无条件放行、**不看权限行的状态**；因此该接口返回账号内**全部**权限码
  （含账号内自定义权限，**不过滤权限状态**）。若这里按状态过滤，就会出现「接口能调通、菜单却不显示」的错位。
- **子账号语义**：返回其角色聚合结果；任一环节（用户、角色、权限）停用或软删除即从结果中消失。
- **客户端义务**：前端必须**直接采用该接口返回的清单**作为唯一权限事实，**不得**再按主体类型（如
  `ADMIN_PRIMARY`）自行推断「拥有全部权限」——那会形成第二份真相。

> 新增无权限码的管理端接口属于**扩大公开面**，必须像本条一样在本文档留痕并说明理由；
> 权限矩阵中的其余接口一律按上表校验权限码。

## 性能与审计

- 角色委派通过一次角色归属批量查询和一次角色权限码批量查询完成，不允许逐角色、逐权限查询。
- 权限委派通过一次权限归属批量查询和一次权限码批量查询完成。
- 关系替换由 Cases 事务包裹，授权校验与关系写入处于同一事务边界。
- 审计日志包含 requestId、accountId、userId、subjectType、操作、权限码、风险级别、结果和耗时；不得记录密码、Token 或完整权限列表。

## 安全不变量

- 不信任外部身份 Header，只接受 Gateway 验证后传播的上下文。
- 所有持久化查询、更新、删除和关系写入必须显式包含 `accountId`。
- 子账号不能修改系统托管权限，不能授予自身未拥有的权限。
- 空列表表示清空关系；重复 ID 去重；空 ID、非正数 ID 和跨账号 ID 拒绝。
- Redis opaque Session 只保存主体和会话状态，不作为权限事实来源。
