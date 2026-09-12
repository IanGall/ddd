-- 渠道长期凭证一次性切换脚本。不迁移 OAuth2 客户端，执行前必须备份旧表。
DROP TABLE IF EXISTS oauth_client;

CREATE TABLE channel_credential (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_code VARCHAR(64) NOT NULL,
    channel_name VARCHAR(128) NOT NULL,
    secret_ciphertext VARBINARY(512) NOT NULL,
    secret_iv BINARY(12) NOT NULL,
    encryption_key_id VARCHAR(32) NOT NULL,
    secret_version BIGINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    last_rotated_at DATETIME NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    updated_by_user_id BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_channel_credential_code (channel_code),
    KEY idx_channel_credential_status (status, deleted, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE channel_data_scope (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_id BIGINT NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    scope_value VARCHAR(128) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 1,
    created_by_user_id BIGINT NOT NULL,
    updated_by_user_id BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_channel_data_scope (channel_id, scope_type, scope_value),
    KEY idx_channel_data_scope_query (channel_id, scope_type, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO rbac_permission
    (account_id, perm_code, perm_name, perm_type, status, system_managed, deleted)
SELECT account.id, permission.perm_code, permission.perm_name, 2, 1, 1, 0
FROM rbac_account account
CROSS JOIN (
    SELECT 'rbac:channel-credential:read' perm_code, '查看渠道凭证' perm_name
    UNION ALL SELECT 'rbac:channel-credential:create', '创建渠道凭证'
    UNION ALL SELECT 'rbac:channel-credential:update', '更新渠道凭证'
    UNION ALL SELECT 'rbac:channel-credential:rotate', '轮换渠道密钥'
    UNION ALL SELECT 'rbac:channel-credential:delete', '删除渠道凭证'
) permission
WHERE account.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM rbac_permission existing
      WHERE existing.account_id = account.id AND existing.perm_code = permission.perm_code
  );
