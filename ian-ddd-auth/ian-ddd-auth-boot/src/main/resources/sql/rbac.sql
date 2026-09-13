-- RBAC 主账号/子账号最终结构。账号 ID 使用 BIGINT，并作为全部 RBAC 数据的隔离键。
DROP TABLE IF EXISTS channel_data_scope;
DROP TABLE IF EXISTS channel_credential;
DROP TABLE IF EXISTS customer_user;
DROP TABLE IF EXISTS rbac_role_permission;
DROP TABLE IF EXISTS rbac_user_role;
DROP TABLE IF EXISTS rbac_permission;
DROP TABLE IF EXISTS rbac_role;
DROP TABLE IF EXISTS rbac_user;
DROP TABLE IF EXISTS rbac_account;
DROP TABLE IF EXISTS channel_credential;

CREATE TABLE rbac_account
(
    id BIGINT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(128) DEFAULT '',
    email         VARCHAR(128) DEFAULT '',
    mobile        VARCHAR(32)  DEFAULT '',
    status      TINYINT      NOT NULL DEFAULT 1,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_rbac_account_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE channel_data_scope
(
    id BIGINT PRIMARY KEY,
    channel_id         BIGINT       NOT NULL,
    scope_type         VARCHAR(32)  NOT NULL,
    scope_value        VARCHAR(128) NOT NULL,
    status             TINYINT      NOT NULL DEFAULT 1,
    version            BIGINT       NOT NULL DEFAULT 1,
    created_by_user_id BIGINT       NOT NULL,
    updated_by_user_id BIGINT       NOT NULL,
    deleted            TINYINT      NOT NULL DEFAULT 0,
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_channel_data_scope (channel_id, scope_type, scope_value),
    KEY idx_channel_data_scope_query (channel_id, scope_type, status, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE rbac_user
(
    id BIGINT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(128)          DEFAULT '',
    email         VARCHAR(128)          DEFAULT '',
    mobile        VARCHAR(32)           DEFAULT '',
    status        TINYINT      NOT NULL DEFAULT 1,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_rbac_user_account_username (account_id, username),
    KEY idx_rbac_user_account_deleted_id (account_id, deleted, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE rbac_role
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    role_code   VARCHAR(64)  NOT NULL,
    role_name   VARCHAR(128) NOT NULL,
    role_desc   VARCHAR(255)          DEFAULT '',
    status      TINYINT      NOT NULL DEFAULT 1,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_rbac_role_account_code (account_id, role_code),
    KEY idx_rbac_role_account_deleted_id (account_id, deleted, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE rbac_permission
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    perm_code   VARCHAR(64)  NOT NULL,
    perm_name   VARCHAR(128) NOT NULL,
    perm_type   INT          NOT NULL DEFAULT 2,
    parent_id   BIGINT       NOT NULL DEFAULT 0,
    path        VARCHAR(255)          DEFAULT '',
    method      VARCHAR(32)           DEFAULT '',
    status      TINYINT      NOT NULL DEFAULT 1,
    system_managed TINYINT NOT NULL DEFAULT 0,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_rbac_permission_account_code (account_id, perm_code),
    KEY idx_rbac_permission_account_deleted_id (account_id, deleted, id),
    KEY idx_rbac_permission_account_parent (account_id, deleted, parent_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE rbac_user_role
(
    account_id BIGINT NOT NULL,
    user_id     BIGINT       NOT NULL,
    role_id     BIGINT       NOT NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, user_id, role_id),
    KEY idx_rbac_user_role_role (account_id, role_id, user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE rbac_role_permission
(
    account_id BIGINT NOT NULL,
    role_id       BIGINT       NOT NULL,
    permission_id BIGINT       NOT NULL,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, role_id, permission_id),
    KEY idx_rbac_role_permission_permission (account_id, permission_id, role_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE customer_user
(
    id BIGINT PRIMARY KEY,
    login_name          VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(128) NOT NULL DEFAULT '',
    avatar        VARCHAR(512) NOT NULL DEFAULT '',
    status        TINYINT      NOT NULL DEFAULT 1,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    password_changed_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at       DATETIME    NULL,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_customer_user_login_name (login_name),
    KEY idx_customer_user_status (status, deleted, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE channel_credential
(
    id BIGINT PRIMARY KEY,
    channel_code       VARCHAR(64)    NOT NULL,
    channel_name       VARCHAR(128)   NOT NULL,
    secret_ciphertext  VARBINARY(512) NOT NULL,
    secret_iv          BINARY(12)     NOT NULL,
    encryption_key_id  VARCHAR(32)    NOT NULL,
    secret_version     BIGINT         NOT NULL,
    status             TINYINT       NOT NULL DEFAULT 1,
    last_rotated_at    DATETIME       NOT NULL,
    created_by_user_id BIGINT         NOT NULL,
    updated_by_user_id BIGINT         NOT NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    create_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_channel_credential_code (channel_code),
    KEY idx_channel_credential_status (status, deleted, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;


-- 主账号及其系统权限必须通过平台开户接口创建，确保账号 ID 来自全局 ID 生成器。
