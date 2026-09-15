-- 收藏 + 降价提醒 迁移（存量库升级）
-- 执行：mysql -uroot -p<密码> -D supermarket_system < deploy/upgrade-favorite-alert.sql

CREATE TABLE IF NOT EXISTS user_favorite (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  price_at_favorite DECIMAL(10,2) NOT NULL COMMENT '收藏当时的售价（降价提醒基线）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_favorite (user_id, product_id),
  KEY idx_user_favorite_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品收藏';

CREATE TABLE IF NOT EXISTS price_alert (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  old_price DECIMAL(10,2) NOT NULL COMMENT '基线价（=收藏时售价）',
  new_price DECIMAL(10,2) NOT NULL COMMENT '检测到降价时的现价',
  drop_amount DECIMAL(10,2) NOT NULL COMMENT '降幅金额 = old_price - new_price',
  is_read TINYINT NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_price_alert (user_id, product_id),
  KEY idx_price_alert_unread (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏商品降价提醒';
