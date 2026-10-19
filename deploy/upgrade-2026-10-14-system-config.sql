-- 存量库迁移：新增 system_config 通用配置表（首页节日氛围开关 festive_theme 改为后台可切）。
-- 新装机不用跑这个：deploy/init.sql 已含建表、db/seed-data.sql 已含种子行（缺行才插）。
-- 用法：mysql -uroot -p --default-character-set=utf8mb4 -D supermarket_system < deploy/upgrade-2026-10-14-system-config.sql

CREATE TABLE IF NOT EXISTS system_config (
    config_key  VARCHAR(60)  NOT NULL COMMENT '配置项 key（主键）',
    config_value VARCHAR(512) NULL     COMMENT '配置值',
    description  VARCHAR(255) NULL     COMMENT '描述/说明',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用系统配置（K/V）';

-- 首页节日氛围开关：daily=日常 / midautumn=中秋 / none=关闭（默认 daily，去掉写死的中秋）。
-- INSERT IGNORE：存量库若已有该 key（理论上没有）则不覆盖，避免误改运营已设的值。
-- 新装机由 db/seed-data.sql 的同一行负责自愈（缺行才插）。
INSERT IGNORE INTO system_config (config_key, config_value, description) VALUES
('festive_theme', 'daily', '首页节日氛围：daily=日常 / midautumn=中秋 / none=关闭');
