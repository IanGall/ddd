-- Auth/RBAC 一次性升级脚本：为 rbac_account 增加 username 唯一约束。
-- 必须先备份 rbac_account，再在发布新版 Provider 前人工执行；本脚本不提供兼容或回滚逻辑。

-- 前置检查：以下查询必须返回空结果；否则先人工合并重复账号，再加唯一键。
SELECT username, COUNT(*) AS duplicate_count
FROM rbac_account
GROUP BY username
HAVING COUNT(*) > 1;

ALTER TABLE rbac_account
    ADD UNIQUE KEY uk_rbac_account_username (username);
