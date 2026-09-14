-- 统一渠道相关表的 ID 策略，使已有库与代码/DDL 一致。人工执行，不提供回滚逻辑。
--
-- 背景：DDL 与实际赋值方式曾不一致——channel_credential / customer_user 声明了 AUTO_INCREMENT 但应用显式赋值；
-- channel_data_scope 没有 AUTO_INCREMENT 而应用显式赋值。前者只是语义混乱，后者在本次改造后会让插入直接失败。
--
-- 改造后的策略：
--   channel_credential  → 应用赋 ID（ChannelCredentialDTO.id 对外暴露，需稳定标识）
--   customer_user       → 应用赋 ID（CustomerUserDTO.id 对外暴露）
--   channel_data_scope  → 数据库自增（从属数据，ID 不出服务、领域模型不携带自身 ID）
--
-- 影响面：三条语句都是元数据变更，不改动任何行数据，可在业务运行中执行。
-- 幂等：对已是目标状态的列重复执行是无害的 no-op，可在多个库上逐个执行。
-- 说明：channel_data_scope 加自增后，计数器从「现有最大 id + 1」起，天然高于历史分布式 ID，不会冲突。

-- 1) 数据范围改用数据库自增
ALTER TABLE channel_data_scope
    MODIFY id BIGINT NOT NULL AUTO_INCREMENT;

-- 2) 渠道凭证保持应用赋 ID，去掉遗留的自增声明
ALTER TABLE channel_credential
    MODIFY id BIGINT NOT NULL;

-- 3) C 端用户保持应用赋 ID，去掉遗留的自增声明
ALTER TABLE customer_user
    MODIFY id BIGINT NOT NULL;

-- 校验：channel_data_scope 应为 auto_increment，channel_credential / customer_user 应为空
SELECT TABLE_NAME, COLUMN_NAME, EXTRA
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND COLUMN_NAME = 'id'
  AND TABLE_NAME IN ('channel_data_scope', 'channel_credential', 'customer_user')
ORDER BY TABLE_NAME;
