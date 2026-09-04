-- 清理购物车/结算流程的验证测试用户及其全部关联数据
-- 关键顺序:user_coupon 的 order_id 外键引用 orders,因此必须先删 user_coupon(按 order_id)再删 orders。

DROP TEMPORARY TABLE IF EXISTS tmp_oids;
CREATE TEMPORARY TABLE tmp_oids (id BIGINT);

INSERT INTO tmp_oids
  SELECT o.id FROM orders o
  JOIN sys_user u ON o.user_id = u.id
  WHERE u.username IN ('cartv_1787993022','cartv_1787993244');

-- 1) 先删依赖 orders 的子表
DELETE FROM payment_record WHERE order_id IN (SELECT id FROM tmp_oids);
DELETE FROM order_item WHERE order_id IN (SELECT id FROM tmp_oids);
DELETE FROM stock_log WHERE order_id IN (SELECT id FROM tmp_oids);
DELETE FROM wallet_transaction WHERE order_id IN (SELECT id FROM tmp_oids);

-- 2) 打破循环外键:orders.user_coupon_id 引用 user_coupon.id,且 user_coupon.order_id 引用 orders.id
--    先把 orders.user_coupon_id 置空,解除 orders -> user_coupon 的依赖
UPDATE orders SET user_coupon_id = NULL WHERE id IN (SELECT id FROM tmp_oids);

-- 3) 关键:删除已用券(user_coupon.order_id 引用 orders),必须在删 orders 之前
DELETE FROM user_coupon WHERE order_id IN (SELECT id FROM tmp_oids);

-- 4) 再删 orders
DELETE FROM orders WHERE id IN (SELECT id FROM tmp_oids);

-- 4) 按 user_id 清理其余子表与用户
DELETE FROM user_coupon WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('cartv_1787993022','cartv_1787993244'));
DELETE FROM wallet_transaction WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('cartv_1787993022','cartv_1787993244'));
DELETE FROM cart_item WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('cartv_1787993022','cartv_1787993244'));
DELETE FROM user_address WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('cartv_1787993022','cartv_1787993244'));
DELETE FROM sys_user WHERE username IN ('cartv_1787993022','cartv_1787993244');

-- 5) 校验
SELECT COUNT(*) AS leftover_users FROM sys_user WHERE username LIKE 'cartv_%';
DROP TEMPORARY TABLE IF EXISTS tmp_oids;
