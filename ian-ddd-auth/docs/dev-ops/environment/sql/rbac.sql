-- RBAC 主账号/子账号基础表与权限表。
-- 主账号记录在 rbac_account，子账号记录在 rbac_user；account_id 使用 BIGINT 作为隔离键。

DROP TABLE IF EXISTS rbac_role_permission;
DROP TABLE IF EXISTS rbac_user_role;
DROP TABLE IF EXISTS rbac_permission;
DROP TABLE IF EXISTS rbac_role;
DROP TABLE IF EXISTS rbac_user;
DROP TABLE IF EXISTS rbac_account;

CREATE TABLE rbac_account
(
    id BIGINT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(128)          DEFAULT '',
    email         VARCHAR(128)          DEFAULT '',
    mobile        VARCHAR(32)           DEFAULT '',
    status        TINYINT      NOT NULL DEFAULT 1,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
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
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
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
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
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

-- 运维初始化模板：请替换占位值后人工执行，不由应用启动自动执行。
INSERT INTO rbac_account (id, username, password_hash, display_name, status, deleted)
VALUES (/* accountId */ 1, /* username */ 'admin',
           /* passwordHash */ '$2a$10$MfJzNoVmCCybGue9dTTQ2u/ZwUu5oRRlJk3ggEkTnpU8x0Kgd7hKe',
           /* displayName */ '主账号', 1, 0);

INSERT INTO rbac_permission (account_id, perm_code, perm_name, perm_type, status, system_managed, deleted)
VALUES (1, 'rbac:user:read', '查看子账号', 2, 1, 1, 0),
       (1, 'rbac:user:create', '创建子账号', 2, 1, 1, 0),
       (1, 'rbac:user:update', '更新子账号', 2, 1, 1, 0),
       (1, 'rbac:user:delete', '删除子账号', 2, 1, 1, 0),
       (1, 'rbac:user-role:read', '查看用户角色', 2, 1, 1, 0),
       (1, 'rbac:user-role:grant', '授予用户角色', 2, 1, 1, 0),
       (1, 'rbac:role:read', '查看角色', 2, 1, 1, 0),
       (1, 'rbac:role:create', '创建角色', 2, 1, 1, 0),
       (1, 'rbac:role:update', '更新角色', 2, 1, 1, 0),
       (1, 'rbac:role:delete', '删除角色', 2, 1, 1, 0),
       (1, 'rbac:role-permission:read', '查看角色权限', 2, 1, 1, 0),
       (1, 'rbac:role-permission:grant', '授予角色权限', 2, 1, 1, 0),
       (1, 'rbac:permission:read', '查看权限', 2, 1, 1, 0),
       (1, 'rbac:permission:create', '创建权限', 2, 1, 1, 0),
       (1, 'rbac:permission:update', '更新权限', 2, 1, 1, 0),
       (1, 'rbac:permission:delete', '删除权限', 2, 1, 1, 0);
