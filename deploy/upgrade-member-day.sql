-- ============================================================
-- 会员日（消费积分翻倍，后台可管）—— 存量库升级脚本
--
-- 背景：「会员日 每月18号 双倍积分」原来只是公告里的一句话，后端并没有实现
--       （同「¥20 新人立减」那类假承诺）。本脚本建 member_day 表并播入默认的 18 号 ×2，
--       之后日期/倍率都在后台「会员日」菜单里调，公告文案会跟着自动更新。
--
-- 执行：mysql -uroot -p --default-character-set=utf8mb4 supermarket_system < deploy/upgrade-member-day.sql
--       （带中文，必须加 --default-character-set=utf8mb4，否则 ERROR 1366）
-- 幂等：重复执行安全。
-- ============================================================

CREATE TABLE IF NOT EXISTS member_day (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    day_of_month INT NOT NULL COMMENT '每月几号（1-31）',
    multiplier DECIMAL(3, 1) NOT NULL DEFAULT 2.0 COMMENT '积分倍率，2 = 双倍',
    remark VARCHAR(60) NOT NULL DEFAULT '' COMMENT '展示用备注',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_day_day (day_of_month),
    KEY idx_member_day_enabled (enabled, day_of_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员日（消费积分翻倍，后台可管）';

-- 默认 18 号双倍（与公告里原来的说法一致）。
-- ⚠️ 用 INSERT IGNORE 而不是 ON DUPLICATE KEY UPDATE：这条会被后台改，
--    回写的话就成了"后台改完一重启被重置"的假功能（公告/热搜词/商品热门标记都踩过这个坑）。
INSERT IGNORE INTO member_day (id, day_of_month, multiplier, remark, enabled)
VALUES (1, 18, 2.0, '会员日', 1);
