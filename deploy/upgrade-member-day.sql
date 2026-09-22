-- ============================================================
-- 会员日（指定日期消费积分翻倍）—— 存量库升级脚本
--
-- 背景：「会员日 每月18号 双倍积分」原来只是公告里的一句话，后端并没有实现
--       （同「¥20 新人立减」那类假承诺）。本脚本建 member_day 表并播入一个默认日期。
--
-- ⚠️ 本脚本同时处理**结构变更**：member_day 是 09-22 当天引入的，初版存的是「每月几号」
--    （`day_of_month`，按月循环），当天下午按用户要求改成「具体日期」（`member_date`）。
--    仍然是旧结构的库会被**重建**（旧数据只有一条默认的 18 号，重建后按「下一个 18 号」播回，
--    没有真实的运营数据会丢）。
--
-- ⚠️ 公告栏那条「会员日…」**不由系统改写** —— 那是运营文案，由管理员自己维护。
--
-- 执行：mysql -uroot -p --default-character-set=utf8mb4 supermarket_system < deploy/upgrade-member-day.sql
--       （带中文，必须加 --default-character-set=utf8mb4，否则 ERROR 1366）
-- 幂等：重复执行安全。
-- ============================================================

-- 旧结构（day_of_month）→ 重建。用 information_schema 判断，避免在已经是新结构时误删。
SET @has_old_column := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member_day' AND COLUMN_NAME = 'day_of_month'
);
SET @drop_old := IF(@has_old_column > 0, 'DROP TABLE member_day', 'DO 0');
PREPARE stmt FROM @drop_old;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS member_day (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    member_date DATE NOT NULL COMMENT '会员日日期（具体年月日）',
    multiplier DECIMAL(3, 1) NOT NULL DEFAULT 2.0 COMMENT '积分倍率，2 = 双倍',
    remark VARCHAR(60) NOT NULL DEFAULT '' COMMENT '展示用备注',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_day_date (member_date),
    KEY idx_member_day_enabled (enabled, member_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员日（指定日期消费积分翻倍）';

-- 默认给「下一个 18 号」（今天就是 18 号则用今天），保证装完就有一个**未过期**的会员日可看。
-- ⚠️ 用 INSERT IGNORE 而不是 ON DUPLICATE KEY UPDATE：这些日期会被后台改，
--    回写的话就成了"后台改完一重启被重置"的假功能（公告/热搜词/商品热门标记都踩过这个坑）。
INSERT IGNORE INTO member_day (id, member_date, multiplier, remark, enabled)
VALUES (
    1,
    IF(DAY(CURDATE()) <= 18,
       DATE_FORMAT(CURDATE(), '%Y-%m-18'),
       DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-18')),
    2.0,
    '会员日',
    1
);
