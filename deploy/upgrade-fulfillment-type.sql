-- 履约方式拆成三种：同城即时配送 / 快递配送 / 门店自提（存量库升级）
--
-- 背景：改造前 fulfillment_type 只有 DELIVERY（送货上门）/ PICKUP（门店自提），
-- 而「后台发货」这一步强制填快递公司+快递单号 —— 快递的规则被套在了所有配送单上，
-- 自提单甚至也被要求填快递单号（不填就永远停在「已付款」，因为确认收货也要求已发货）。
--
-- 现在拆成三种互斥的交付形态：
--   INSTANT 同城即时配送：商家自有运力，要地址，可选 2 小时时段，**不收运费**
--   EXPRESS 快递配送：第三方物流，要地址，需运单号，**商品小计满 ¥99 免运费，否则 ¥8**
--   PICKUP  门店自提：要门店，生成自提码，**无物流**，后台改走「备货完成」
--
-- 执行：mysql -uroot -p<密码> --default-character-set=utf8mb4 -D supermarket_system < deploy/upgrade-fulfillment-type.sql
-- 注意：必须带 --default-character-set=utf8mb4，否则中文注释会报 ERROR 1366

-- 1) 列定义：默认值改为 INSTANT，注释写清三个取值
ALTER TABLE orders
  MODIFY COLUMN fulfillment_type VARCHAR(20) NOT NULL DEFAULT 'INSTANT'
  COMMENT 'INSTANT 同城即时配送 / EXPRESS 快递配送 / PICKUP 门店自提';

-- 2) 存量迁移：旧的 DELIVERY（当时"配送"不分即时/快递，带地址、可选时段）语义上就是同城即时配送。
--    放在 MODIFY 之后执行，避免旧默认值又被写进来。
UPDATE orders SET fulfillment_type = 'INSTANT' WHERE fulfillment_type = 'DELIVERY';

-- 3) 校验：应只剩三种取值，且不再有 DELIVERY
--    SELECT fulfillment_type, COUNT(*) FROM orders GROUP BY fulfillment_type;
