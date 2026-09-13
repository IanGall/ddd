-- 执行前必须备份 rbac_permission，并先在预发布环境验证。
ALTER TABLE rbac_permission
    ADD COLUMN system_managed TINYINT NOT NULL DEFAULT 0 AFTER status;

-- 旧版允许创建 rbac: 前缀权限；切换后统一视为系统权限并冻结运行时变更。
UPDATE rbac_permission
SET system_managed = 1
WHERE perm_code LIKE 'rbac:%';

-- 验收：以下查询必须返回 0 行。
SELECT id, account_id, perm_code
FROM rbac_permission
WHERE system_managed = 1
  AND perm_code NOT LIKE 'rbac:%';

-- 验收：系统权限在同一账号内不得存在重复编码；唯一索引正常时必须返回 0 行。
SELECT account_id, perm_code, COUNT(*) AS duplicate_count
FROM rbac_permission
WHERE system_managed = 1
  AND deleted = 0
GROUP BY account_id, perm_code
HAVING COUNT(*) > 1;
