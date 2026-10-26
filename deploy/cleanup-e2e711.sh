#!/usr/bin/env bash
# 清理 e2e-711 测试残留（userId 由 username 解析），FK 顺序删除 + 回补库存。零残留。
set -euo pipefail
ENVLINE="$(systemctl show supermarket -p Environment | sed 's/^Environment=//')"
U="$(printf '%s\n' "$ENVLINE" | tr ' ' '\n' | sed -n 's/^DB_USERNAME=//p' | head -1)"
P="$(printf '%s\n' "$ENVLINE" | tr ' ' '\n' | sed -n 's/^DB_PASSWORD=//p' | head -1)"
DB="supermarket_system"
q() { mysql -u"$U" -p"$P" "$DB" -N -e "$1" 2>/dev/null; }
run() { mysql -u"$U" -p"$P" "$DB" -e "$1" 2>/dev/null; }

TUID="$(q "SELECT id FROM sys_user WHERE username='e2e711_011600'")"
[ -n "$TUID" ] || { echo "NO_TEST_USER"; exit 0; }
echo "清理 userId=$TUID"

echo "该用户订单: $(q "SELECT GROUP_CONCAT(id) FROM orders WHERE user_id=$TUID")"

# 1) 回补库存（按该用户订单的 order_item 数量）
run "UPDATE product p JOIN (SELECT oi.product_id, SUM(oi.quantity) qty FROM order_item oi JOIN orders o ON o.id=oi.order_id WHERE o.user_id=$TUID GROUP BY oi.product_id) t ON p.id=t.product_id SET p.stock=p.stock+t.qty;"
echo "库存已回补（矿泉水 id=2 现 $(q 'SELECT stock FROM product WHERE id=2')）"

# 2) FK 顺序删除（每步独立，避免大事务）
run "DELETE FROM return_request WHERE user_id=$TUID;"
run "DELETE oi FROM order_item oi JOIN orders o ON o.id=oi.order_id WHERE o.user_id=$TUID;"
run "DELETE pr FROM payment_record pr JOIN orders o ON o.id=pr.order_id WHERE o.user_id=$TUID;"
run "UPDATE orders SET user_coupon_id=NULL WHERE user_id=$TUID;"
run "UPDATE stock_log SET order_id=NULL WHERE order_id IN (SELECT id FROM orders WHERE user_id=$TUID);"
run "UPDATE stock_log SET operator_id=NULL WHERE operator_id=$TUID;"
for t in product_review wallet_transaction user_message point_ledger user_favorite price_alert cart_item user_address user_coupon; do
  run "DELETE FROM $t WHERE user_id=$TUID;"
done
run "DELETE FROM orders WHERE user_id=$TUID;"
run "DELETE FROM sys_user WHERE id=$TUID;"

echo "残留核对: user=$(q "SELECT COUNT(*) FROM sys_user WHERE id=$TUID") orders=$(q "SELECT COUNT(*) FROM orders WHERE user_id=$TUID") addr=$(q "SELECT COUNT(*) FROM user_address WHERE user_id=$TUID")"
echo "CLEANUP_DONE"
