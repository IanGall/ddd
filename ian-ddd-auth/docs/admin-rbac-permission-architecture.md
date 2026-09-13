# Admin RBAC 权限架构

## 权限边界

Admin 请求采用三层权限边界：

1. Gateway 只校验 opaque Token、可信身份和 `ADMIN_PRIMARY` / `ADMIN_SUB_ACCOUNT` 主体分区。
2. Cases 在每个用例入口按 `RbacPermissionCode` 执行权限校验并记录授权审计。
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

主账号拥有账号内全部权限，但账号必须仍处于有效状态。权限不足、跨账号对象和对象不存在统一返回拒绝，不向调用方泄露其他账号对象是否存在。

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
