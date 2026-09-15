-- 履约（门店自提 + 配送时段）+ 消息中心 迁移（存量库升级）
-- 执行：mysql -uroot -p<密码> --default-character-set=utf8mb4 -D supermarket_system < deploy/upgrade-fulfillment-message.sql
-- 注意：必须带 --default-character-set=utf8mb4，否则中文种子数据会报 ERROR 1366

-- 1) 订单履约字段：老订单默认按「送货上门」处理，不会因为 NOT NULL 报错
ALTER TABLE orders
  ADD COLUMN fulfillment_type VARCHAR(20) NOT NULL DEFAULT 'DELIVERY' COMMENT 'DELIVERY 送货上门 / PICKUP 门店自提',
  ADD COLUMN pickup_store_id BIGINT DEFAULT NULL COMMENT '自提门店 id',
  ADD COLUMN pickup_store_name VARCHAR(80) DEFAULT NULL COMMENT '自提门店名快照',
  ADD COLUMN pickup_code VARCHAR(16) DEFAULT NULL COMMENT '自提码（订单号后 6 位）',
  ADD COLUMN delivery_slot VARCHAR(60) DEFAULT NULL COMMENT '配送时段快照，如 2026-09-15 09:00-11:00';

-- 2) 门店 / 自提点
CREATE TABLE IF NOT EXISTS store (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  address VARCHAR(255) NOT NULL,
  phone VARCHAR(20) DEFAULT NULL,
  business_hours VARCHAR(60) DEFAULT NULL COMMENT '营业/自提时间',
  city VARCHAR(40) DEFAULT NULL,
  district VARCHAR(40) DEFAULT NULL,
  pickup_notice VARCHAR(255) DEFAULT NULL COMMENT '自提须知',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 营业 0 停业',
  sort_no INT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_store_name (name),
  KEY idx_store_status_sort (deleted, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店/自提点';

-- 3) 站内消息（消息中心）
CREATE TABLE IF NOT EXISTS user_message (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  type VARCHAR(20) NOT NULL COMMENT 'ORDER/MEMBER/COUPON/SYSTEM',
  title VARCHAR(120) NOT NULL,
  content VARCHAR(500) DEFAULT NULL,
  link_view VARCHAR(40) DEFAULT NULL COMMENT '跳转目标：orders/orderDetail/coupons/points/shop/favorites',
  link_ref VARCHAR(64) DEFAULT NULL COMMENT '跳转参数（订单 id 等）',
  dedupe_key VARCHAR(120) DEFAULT NULL COMMENT '幂等键',
  is_read TINYINT NOT NULL DEFAULT 0 COMMENT '0 未读 1 已读',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_message_dedupe (user_id, dedupe_key),
  KEY idx_user_message_unread (user_id, is_read),
  KEY idx_user_message_type (user_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息';

-- 4) 门店种子数据（自提点演示；重复执行不会覆盖后台已改的内容）
INSERT INTO store (name, address, phone, business_hours, city, district, pickup_notice, status, sort_no, deleted) VALUES
  ('南山科技园店', '深圳市南山区科技园南区 8 栋 1 层', '0755-8600 1001', '08:00-22:00', '深圳市', '南山区', '下单后约 1 小时可自提，凭自提码到服务台取货。', 1, 10, 0),
  ('福田购物公园店', '深圳市福田区购物公园 B1 层 B103', '0755-8600 1002', '07:30-22:30', '深圳市', '福田区', '地下一层生鲜区旁，冷链商品由店员协助打包。', 1, 20, 0),
  ('宝安中心店', '深圳市宝安区中心路 66 号 1 层', '0755-8600 1003', '08:00-21:30', '深圳市', '宝安区', '自提请出示自提码，可代取（需报手机号后四位）。', 1, 30, 0)
ON DUPLICATE KEY UPDATE name = VALUES(name);
