-- 第三档：限时秒杀 + 协议/隐私正文（存量库升级）
--
-- 内容：
--   1) flash_sale 表（限时秒杀场次，含独立名额）
--   2) order_item.flash_sale_id（秒杀名额回退用的快照列）
--   3) legal_doc 表（协议/隐私正文，后台可编辑；正文由应用启动时幂等写入模板）
--   4) 一条秒杀演示场次
--
-- 执行：mysql -uroot -p<密码> --default-character-set=utf8mb4 -D supermarket_system < deploy/upgrade-flash-sale.sql
-- 注意：必须带 --default-character-set=utf8mb4，否则中文数据会报 ERROR 1366

-- 1) 限时秒杀场次
--    名额（total_quota/sold_quota）与商品库存相互独立：名额抢完即结束，即使商品还有库存。
--    sold_quota 靠带条件的 UPDATE 原子扣减防超卖，取消/超时/退款时回退。
CREATE TABLE IF NOT EXISTS flash_sale (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80) NOT NULL COMMENT '场次名，如「早市秒杀」',
  product_id BIGINT NOT NULL,
  flash_price DECIMAL(10,2) NOT NULL COMMENT '秒杀价（必须低于商品售价）',
  total_quota INT NOT NULL COMMENT '秒杀总名额',
  sold_quota INT NOT NULL DEFAULT 0 COMMENT '已抢名额',
  per_user_limit INT NOT NULL DEFAULT 0 COMMENT '每人限购件数，0=不限',
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
  sort_no INT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_flash_sale_window (deleted, status, start_time, end_time),
  KEY idx_flash_sale_product (product_id, deleted, status, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='限时秒杀场次';

-- 2) 订单行的秒杀快照列
--    秒杀价本身体现在 order_item.product_price（与会员价同一套"替换单价"口径），
--    这里只记录场次 id，供取消/超时/退款时回退名额。
ALTER TABLE order_item
  ADD COLUMN flash_sale_id BIGINT DEFAULT NULL COMMENT '命中的秒杀场次 id（回退名额用）' AFTER original_price;

-- 3) 协议 / 隐私等法律正文（后台可编辑）
--    正文由应用启动时幂等写入模板（LegalDocService.ensureDefaults），这里只建表：
--    正文含大量中文与换行，放 SQL 里转义易错，交给应用写入更可靠且不会覆盖后台已改内容。
CREATE TABLE IF NOT EXISTS legal_doc (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  doc_key VARCHAR(40) NOT NULL COMMENT 'TERMS / PRIVACY',
  title VARCHAR(120) NOT NULL,
  content TEXT NOT NULL COMMENT '正文，含【】占位符，上线前须替换',
  version VARCHAR(20) DEFAULT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  sort_no INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_legal_doc_key (doc_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='协议/隐私等法律文本';

-- 4) 秒杀演示场次：取一个在售、有库存的商品，档期设为「今天 00:00 ~ 30 天后」
--    只在商品存在且当前没有未结束场次时插入，重复执行不会叠加。
INSERT INTO flash_sale (name, product_id, flash_price, total_quota, sold_quota, per_user_limit,
                        start_time, end_time, status, sort_no, deleted)
SELECT CONCAT(p.name, ' 限时秒杀'), p.id, ROUND(p.price * 0.75, 2), 30, 0, 2,
       DATE_FORMAT(CURDATE(), '%Y-%m-%d 00:00:00'),
       DATE_ADD(CURDATE(), INTERVAL 30 DAY),
       1, 10, 0
FROM product p
WHERE p.deleted = 0
  AND p.status = 'ON_SALE'
  AND p.price > 1
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT product_id, end_time, deleted, status FROM flash_sale) f
      WHERE f.deleted = 0 AND f.end_time > NOW()
  )
ORDER BY p.id
LIMIT 1;

-- 5) 自检：确认三处结构就位
SELECT
  (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'order_item' AND column_name = 'flash_sale_id') AS order_item_flash_col,
  (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'flash_sale') AS flash_sale_table,
  (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'legal_doc') AS legal_doc_table,
  (SELECT COUNT(*) FROM flash_sale WHERE deleted = 0) AS demo_flash_sales;
