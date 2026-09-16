-- 统一 user_order 分表的主键策略：由应用侧全局 ID 生成器赋值，不再用分片内自增。
-- 人工执行，不提供回滚逻辑。
--
-- 背景：user_order_0..3 原为 bigint AUTO_INCREMENT。这张逻辑表按 user_id 分片到 4 张物理表，
-- 自增计数在每张物理表内独立，因此 id 会跨分片重号，无法承担「全局唯一主键」；而该表确实需要
-- 全局唯一主键（order_id / uuid 是业务键，且随机值做主键会导致 InnoDB 页分裂与随机 I/O）。
--
-- 改造后：id 由 UserOrderPO 上的 @IdGenerator 在 insert 时填充（业务名 user-order，
-- 见 application.yml 的 ddd.id-generator.businesses）。分片路由按 user_id，与 id 无关，
-- 因此应用侧提前生成主键不影响路由。
--
-- 影响面：四条语句都是元数据变更，不改动任何行数据。去掉 AUTO_INCREMENT 不影响既有行的 id，
-- 新行由应用显式赋值。注意改造后 id 值域会从小整数跳到雪花量级，ORDER BY id 不再跨这条边界
-- 等价于插入顺序。
--
-- 重要：MySQL 的 MODIFY COLUMN 需要完整的新定义，省略 COMMENT 会清掉既有列注释，因此这里必须重复写。
--
-- 幂等：对已去掉 AUTO_INCREMENT 的列重复执行是 no-op，可在多个库上逐个执行。
--
-- 执行范围：dev 的 ian_dev_tech_db_00 / ian_dev_tech_db_01 与测试的 ian_test_tech_db_00 /
-- ian_test_tech_db_01，共四个库各执行一次。

ALTER TABLE user_order_0 MODIFY COLUMN id BIGINT UNSIGNED NOT NULL
    COMMENT '主键；由应用通过全局 ID 生成器赋值（见 UserOrderPO 上的 @IdGenerator），跨分片全局唯一。【不要再改回分片内自增：user_order_0..3 各自计数会跨分片重号】也不要用 order_id 这类随机值做主键，会导致 innodb 内部 page 分裂与大量随机 I/O。';

ALTER TABLE user_order_1 MODIFY COLUMN id BIGINT UNSIGNED NOT NULL
    COMMENT '主键；由应用通过全局 ID 生成器赋值（见 UserOrderPO 上的 @IdGenerator），跨分片全局唯一。【不要再改回分片内自增：user_order_0..3 各自计数会跨分片重号】也不要用 order_id 这类随机值做主键，会导致 innodb 内部 page 分裂与大量随机 I/O。';

ALTER TABLE user_order_2 MODIFY COLUMN id BIGINT UNSIGNED NOT NULL
    COMMENT '主键；由应用通过全局 ID 生成器赋值（见 UserOrderPO 上的 @IdGenerator），跨分片全局唯一。【不要再改回分片内自增：user_order_0..3 各自计数会跨分片重号】也不要用 order_id 这类随机值做主键，会导致 innodb 内部 page 分裂与大量随机 I/O。';

ALTER TABLE user_order_3 MODIFY COLUMN id BIGINT UNSIGNED NOT NULL
    COMMENT '主键；由应用通过全局 ID 生成器赋值（见 UserOrderPO 上的 @IdGenerator），跨分片全局唯一。【不要再改回分片内自增：user_order_0..3 各自计数会跨分片重号】也不要用 order_id 这类随机值做主键，会导致 innodb 内部 page 分裂与大量随机 I/O。';

-- 校验：EXTRA 应为空（不再是 auto_increment），且四个库的四张表都应出现
SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, EXTRA
FROM information_schema.COLUMNS
WHERE TABLE_NAME LIKE 'user_order/_%' ESCAPE '/'
  AND COLUMN_NAME = 'id'
ORDER BY TABLE_SCHEMA, TABLE_NAME;
