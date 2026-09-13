-- Auth/RBAC v2 一次性升级脚本。
-- 必须先备份 rbac_permission，再在发布新版 Provider 前人工执行；本脚本不提供兼容或回滚逻辑。

ALTER TABLE rbac_permission
    ADD COLUMN system_managed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为系统内置权限'
        AFTER status;

UPDATE rbac_permission
SET system_managed = 1
WHERE perm_code IN (
    'rbac:user:read',
    'rbac:user:create',
    'rbac:user:update',
    'rbac:user:delete',
    'rbac:user-role:read',
    'rbac:user-role:grant',
    'rbac:role:read',
    'rbac:role:create',
    'rbac:role:update',
    'rbac:role:delete',
    'rbac:role-permission:read',
    'rbac:role-permission:grant',
    'rbac:permission:read',
    'rbac:permission:create',
    'rbac:permission:update',
    'rbac:permission:delete',
    'rbac:channel-credential:read',
    'rbac:channel-credential:create',
    'rbac:channel-credential:update',
    'rbac:channel-credential:rotate',
    'rbac:channel-credential:delete'
);

-- 以下查询必须全部返回空结果；否则停止发布并先修复异常数据。
SELECT account_id, perm_code, COUNT(*) AS duplicate_count
FROM rbac_permission
WHERE deleted = 0
GROUP BY account_id, perm_code
HAVING COUNT(*) > 1;

SELECT id, account_id, perm_code, system_managed
FROM rbac_permission
WHERE deleted = 0
  AND (
    (perm_code LIKE 'rbac:%' AND system_managed <> 1)
    OR (perm_code NOT LIKE 'rbac:%' AND system_managed <> 0)
  );
