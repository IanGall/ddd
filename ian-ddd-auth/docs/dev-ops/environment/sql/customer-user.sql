CREATE TABLE customer_user (
    -- ID 由应用通过全局 ID 生成器赋值（CustomerUserDTO.id 对外暴露），不使用自增
    id BIGINT PRIMARY KEY,
    login_name VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(128) NOT NULL DEFAULT '',
    avatar VARCHAR(512) NOT NULL DEFAULT '',
    status TINYINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    password_changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_customer_user_login_name (login_name),
    KEY idx_customer_user_status (status, deleted, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE channel_credential (
    -- ID 由应用通过全局 ID 生成器赋值（ChannelCredentialDTO.id 对外暴露），不使用自增
    id BIGINT PRIMARY KEY,
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
