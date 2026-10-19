-- 2026-10-16 order_item.refunded：按商品行维度售后（#9）配套列 + 存量回填口径
--
-- 背景与坑（务必看）：
--   ddl-auto=update 会把实体 OrderItem.refunded 建成 `tinyint(1) NOT NULL DEFAULT 0`，
--   列默认值看起来没问题，但 **Hibernate 遇到 Java 字段为 null 时会显式往 INSERT 里写 NULL，
--   NOT NULL 约束直接拒绝** —— 生产表现：所有新建订单插 order_item 全部 1048 报错，
--   立即购买 / 购物车结算双双 500（前端又吞了错误，用户看到的是「点提交没反应」）。
--
--   根治不在 DB 而在实体：OrderItem.refunded 字段初始化为 false（见 OrderItem.java
--   与 OrderService.buildOrderItem 的 setRefunded(false) 双保险）。本脚本用于：
--     ① 未启用 ddl-auto 的环境补列；② 生产列定义核对；③ 既有环境无存量 NULL 时可作 no-op 核对。
--
-- 生产当前状态（2026-10-16 核对）：列已存在且定义为
--   `refunded` tinyint(1) NOT NULL DEFAULT '0'，存量 3289 行 NULL 数为 0。
--   真正的修复 = 部署含实体默认值的新 jar，**不需要对本表跑数据修复**。

-- ① 无列环境补列（有列则会报 Duplicate column，忽略即可）：
-- ALTER TABLE order_item ADD COLUMN refunded tinyint(1) NOT NULL DEFAULT 0 COMMENT '该行是否已退货退款';

-- ② 列定义核对 / 修正为统一口径：
-- ALTER TABLE order_item MODIFY COLUMN refunded tinyint(1) NOT NULL DEFAULT 0 COMMENT '该行是否已退货退款';

-- ③ 存量 NULL 兜底回填（正常不该有，防早期插入残留）：
-- UPDATE order_item SET refunded = 0 WHERE refunded IS NULL;
