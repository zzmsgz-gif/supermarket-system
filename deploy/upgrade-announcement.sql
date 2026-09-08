-- 存量库升级：新增 announcement（商城公告）表
-- 用于首页右侧「商城公告」面板；前端 GET /api/announcements 读取。
CREATE TABLE IF NOT EXISTS `announcement` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `title` VARCHAR(120) NOT NULL COMMENT '公告标题',
    `content` VARCHAR(500) NOT NULL COMMENT '公告内容/摘要',
    `type` VARCHAR(20) NOT NULL DEFAULT 'NOTICE' COMMENT 'NOTICE/PROMOTION/ACTIVITY/WARNING',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT 'Display order, asc',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
    `publish_time` DATETIME NOT NULL COMMENT 'Publish time',
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_announcement_enabled_sort` (`enabled`, `sort_order`, `publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商城公告';
