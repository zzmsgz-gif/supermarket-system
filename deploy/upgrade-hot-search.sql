-- 存量库迁移：新增 hot_search 表（首页头部「热搜」词条改为后台可管）
-- 新装机不用跑这个，deploy/init.sql 里已含建表；默认词由 DataSeeder 读 db/seed-data.sql 播（INSERT IGNORE）。
-- 用法：mysql -uroot -p --default-character-set=utf8mb4 -D supermarket_system < deploy/upgrade-hot-search.sql

CREATE TABLE IF NOT EXISTS hot_search (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    keyword VARCHAR(30) NOT NULL COMMENT '点击后实际搜索的词（对应 /shop?kw=）',
    label VARCHAR(30) NULL COMMENT '前台展示文案，可空；空则前台回落成 keyword（展示词与搜索词可以不同）',
    sort_order INT NOT NULL DEFAULT 0 COMMENT 'Display order, asc',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_hot_search_enabled_sort (enabled, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='首页热搜词（后台可管）';

-- 默认词刻意不在这里 INSERT：与 db/seed-data.sql 保持"单一来源"，避免两边不一致。
-- 本机执行完本脚本后重启一次后端，DataSeeder 会自动补上默认词（缺行才插）。
