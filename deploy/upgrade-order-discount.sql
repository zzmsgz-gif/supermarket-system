-- 订单优惠信息补全（存量库升级）
-- 背景：订单详情原来只能展示「券 + 活动」两项优惠，会员等级折扣虽然算了却没展示，
--       积分抵扣更是只存了点数(points_used)、没有对应金额，导致
--       「商品小计 - 已展示优惠」≠ 实付，账目对不上。
-- 本次补两列，使订单详情的价目链恒等：
--       商品小计 + 运费 - 券 - 活动 - 会员折扣 - 积分抵扣 = 实付
--
-- 执行：mysql -uroot -p<密码> --default-character-set=utf8mb4 -D supermarket_system < deploy/upgrade-order-discount.sql
-- 注意：必须带 --default-character-set=utf8mb4，否则中文券名回填会报 ERROR 1366

-- 1) 积分抵扣金额（落库的是"实际抵扣额"，不靠 points_used/100 现算：
--    兑换比例将来若调整，历史订单仍要按当时实际抵扣额呈现）
ALTER TABLE orders
  ADD COLUMN points_discount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '积分抵扣金额' AFTER member_discount;

-- 2) 优惠券名称快照（券被改名/删除后，订单详情仍能说清用的是哪张券；与 activity_name 同思路）
ALTER TABLE orders
  ADD COLUMN coupon_name VARCHAR(120) DEFAULT NULL COMMENT '优惠券名称快照' AFTER user_coupon_id;

-- 3) 历史订单回填：积分抵扣金额 = 点数 / 100（现行兑换比例 100 积分 = 1 元）
UPDATE orders
SET points_discount = ROUND(points_used / 100, 2)
WHERE points_used > 0;

-- 4) 历史订单回填券名（订单只存了 user_coupon_id，回溯到券表取名字）
UPDATE orders o
JOIN user_coupon uc ON uc.id = o.user_coupon_id
JOIN coupon c ON c.id = uc.coupon_id
SET o.coupon_name = c.name
WHERE o.user_coupon_id IS NOT NULL
  AND o.coupon_name IS NULL;

-- 5) 订单行加划线价（原价）快照，使订单详情能展示结算页同样有的「划线优惠（已省）」
--    该行只是"已省多少"的信息展示，不参与实付扣减，因此历史订单不回填（当时的原价已不可考），
--    留 NULL 时前端不展示该行，不会造成账目错乱。
ALTER TABLE order_item
  ADD COLUMN original_price DECIMAL(10,2) DEFAULT NULL COMMENT '下单时划线价快照，仅用于展示已省金额' AFTER product_price;

-- 6) 自检：回填后应当没有任何一张订单对不上账，输出行数应为 0
SELECT COUNT(*) AS mismatched_orders
FROM orders
WHERE total_amount + freight_amount - discount_amount - activity_discount - member_discount - points_discount
      <> pay_amount;
