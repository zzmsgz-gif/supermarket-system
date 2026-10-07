-- =====================================================================
-- 秒杀品库存与名额对账（2026-10-07）
--
-- 背景：秒杀品的 product.stock 与 flash_sale 名额是**两条独立的加减路径**
--   建场次：stock = total_quota
--   下单  ：stock -= qty（OrderService.deductStocks）
--   取消/关单/退款：stock += qty（returnStocks）
-- 名额则只在 reserveQuota / releaseQuota 里动。
-- 历史数据（种子脚本 place-*、reset-catalog-* 直接写库）绕过了服务层，
-- 于是两条路径对不上：实测 159 差 2、161 差 1、162 差 5。
--
-- 后果：加购/库存校验用 product.stock，会**多放行** diff 件 ——
--   用户加购成功、到结算才被 409「名额已抢完」拒绝（2026-10-07 用户反馈）。
--
-- 修法：以「有效订单实际占用」为准重算 stock。**只看未取消/未关闭的订单**：
--   场次 20 的 sold_quota=5 对应 2 笔真实 PAID 订单，是真卖出去的，不能清零。
-- =====================================================================

-- 先看会改什么（务必先跑这段确认）
SELECT p.id                AS product_id,
       p.name              AS product_name,
       p.stock             AS stock_before,
       f.total_quota,
       f.sold_quota,
       f.total_quota - f.sold_quota          AS quota_left,
       COALESCE(active.used, 0)              AS active_used,
       (f.total_quota - COALESCE(active.used, 0)) AS correct_stock,
       p.stock - (f.total_quota - COALESCE(active.used, 0)) AS will_change_by
  FROM product p
  JOIN flash_sale f
    ON f.product_id = p.id AND f.deleted = 0
  LEFT JOIN (
        SELECT oi.flash_sale_id, SUM(oi.quantity) AS used
          FROM order_item oi
          JOIN orders o ON o.id = oi.order_id
         WHERE oi.flash_sale_id IS NOT NULL
           AND o.status IN ('PENDING_PAYMENT', 'PAID', 'SHIPPED', 'COMPLETED')
         GROUP BY oi.flash_sale_id
  ) active ON active.flash_sale_id = f.id
 WHERE p.deleted = 0
   AND p.kind = 'FLASH'
   AND p.stock <> (f.total_quota - COALESCE(active.used, 0));


-- ====================== 确认无误后再执行以下 UPDATE ======================

-- 1) 修正 product.stock = 总名额 − 有效订单占用
UPDATE product p
  JOIN flash_sale f
    ON f.product_id = p.id AND f.deleted = 0
  LEFT JOIN (
        SELECT oi.flash_sale_id, SUM(oi.quantity) AS used
          FROM order_item oi
          JOIN orders o ON o.id = oi.order_id
         WHERE oi.flash_sale_id IS NOT NULL
           AND o.status IN ('PENDING_PAYMENT', 'PAID', 'SHIPPED', 'COMPLETED')
         GROUP BY oi.flash_sale_id
  ) active ON active.flash_sale_id = f.id
   SET p.stock = GREATEST(f.total_quota - COALESCE(active.used, 0), 0)
 WHERE p.deleted = 0
   AND p.kind = 'FLASH'
   AND p.stock <> GREATEST(f.total_quota - COALESCE(active.used, 0), 0);

-- 2) 修正 flash_sale.sold_quota = 有效订单实际占用
--    sold_quota 里混着已取消/关闭订单占用的名额（场次 20 的 5 件里只有 2 件真卖出），
--    不清会让「已抢 N 件」虚高、也会误导上面的 stock 计算。
UPDATE flash_sale f
   SET f.sold_quota = COALESCE((
         SELECT SUM(oi.quantity)
           FROM order_item oi
           JOIN orders o ON o.id = oi.order_id
          WHERE oi.flash_sale_id = f.id
            AND o.status IN ('PENDING_PAYMENT', 'PAID', 'SHIPPED', 'COMPLETED')
       ), 0)
 WHERE f.deleted = 0
   AND f.sold_quota <> COALESCE((
         SELECT SUM(oi.quantity)
           FROM order_item oi
           JOIN orders o ON o.id = oi.order_id
          WHERE oi.flash_sale_id = f.id
            AND o.status IN ('PENDING_PAYMENT', 'PAID', 'SHIPPED', 'COMPLETED')
       ), 0);

-- 3) 核对：跑完应该返回空集
SELECT p.id, p.stock, f.total_quota, f.sold_quota,
       p.stock + f.sold_quota AS sum_check
  FROM product p
  JOIN flash_sale f ON f.product_id = p.id AND f.deleted = 0
 WHERE p.deleted = 0
   AND p.kind = 'FLASH'
   AND p.stock + f.sold_quota <> f.total_quota;
