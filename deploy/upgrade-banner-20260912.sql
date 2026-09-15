-- 首页轮播位（banner）升级脚本：2026-09-12
-- 背景：轮播此前由活动自动生成（纯文字），现支持管理员维护带图轮播位。
-- 修订（同日）：按用户要求轮播为纯图片、不叠加任何文字，去除 title/subtitle/tag 列。
USE supermarket_system;

CREATE TABLE IF NOT EXISTS banner (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  image_url VARCHAR(500) NOT NULL COMMENT '轮播图片（本地/OSS uploads）',
  link_product_id BIGINT UNSIGNED DEFAULT NULL COMMENT '点击跳转商品（可空）',
  sort_order INT NOT NULL DEFAULT 0,
  enabled TINYINT NOT NULL DEFAULT 1,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_banner_enabled (enabled, deleted, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='首页轮播位';

-- ↓ 仅「建过旧版（带文字列）」的库需要执行；全新安装跳过这三行。
-- ALTER TABLE banner DROP COLUMN title;
-- ALTER TABLE banner DROP COLUMN subtitle;
-- ALTER TABLE banner DROP COLUMN tag;
ALTER TABLE banner MODIFY image_url VARCHAR(500) NOT NULL COMMENT '轮播图片（本地/OSS uploads）';
