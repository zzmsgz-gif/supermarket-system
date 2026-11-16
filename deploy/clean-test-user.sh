#!/usr/bin/env bash
#
# 按「精确用户名」清除测试账号及其全部关联数据（生产库）。
#
# 为什么要有这个脚本：
# 之前每次都是手写一段 `SET @uid := (SELECT id FROM sys_user WHERE username='xxx')`
# 再用 `CONCAT('…', IFNULL(@uid,'NULL'), …)` 判断是否命中 —— 输出经常显示 NULL/空，
# 但 DELETE 其实已经生效（2026-11-15 因此两次误判"清理没跑"）。
# 这里改成**在 bash 里取到 id 字符串 → 断言非空 → 再拼进 SQL**，路径唯一、可断言。
#
# 用法：
#   ./clean-test-user.sh <username>              # 先打印将删除的内容，再执行
#   ./clean-test-user.sh <username> --dry-run    # 只打印，不删
#
# 可用环境变量覆盖：SM_HOST（默认 root@8.218.154.150）、SM_DB（默认 supermarket_system）
set -uo pipefail

HOST="${SM_HOST:-root@8.218.154.150}"
DB="${SM_DB:-supermarket_system}"
NAME="${1:-}"
MODE="${2:-}"

if [ -z "$NAME" ]; then
  echo "用法: $0 <username> [--dry-run]"
  exit 2
fi

SSH_OPTS=(-o BatchMode=yes -o ConnectTimeout=20)
q() { ssh "${SSH_OPTS[@]}" "$HOST" "mysql -N -B --default-character-set=utf8mb4 $DB -e \"$1\"" 2>/dev/null | grep -viE 'store now|post-quantum|WARNING'; }

# 1) 取 id：空值就中止（绝不用 @uid 中转）
UID_VAL=$(q "SELECT IFNULL(id,'') FROM sys_user WHERE username='$NAME';" | tr -d '\r\n ')
if [ -z "$UID_VAL" ]; then
  echo "账号 [$NAME] 不存在，无需清理。"
  exit 0
fi
echo "目标账号: $NAME (id=$UID_VAL)"

# 2) 打印将受影响的数据量
echo "--- 将删除的数据 ---"
q "SELECT CONCAT('  订单=', (SELECT COUNT(*) FROM orders WHERE user_id=$UID_VAL),
                ' 购物车=', (SELECT COUNT(*) FROM cart_item WHERE user_id=$UID_VAL),
                ' 钱包流水=', (SELECT COUNT(*) FROM wallet_transaction WHERE user_id=$UID_VAL),
                ' 售后=', (SELECT COUNT(*) FROM return_request WHERE order_id IN (SELECT id FROM orders WHERE user_id=$UID_VAL)),
                ' 积分流水=', (SELECT COUNT(*) FROM point_ledger WHERE user_id=$UID_VAL),
                ' 优惠券=', (SELECT COUNT(*) FROM user_coupon WHERE user_id=$UID_VAL));"

if [ "$MODE" = "--dry-run" ]; then
  echo "（--dry-run：未执行删除）"
  exit 0
fi

# 3) 执行删除：子表 → 主表，最后一条带 username 双保险
#
# ⚠️ 顺序坑（2026-11-16 实测踩到，账号残留删不掉）：
#   orders 被 **7 张表** 的外键引用（order_item / payment_record / product_review /
#   return_request / stock_log / user_coupon / wallet_transaction），
#   其中 wallet_transaction、user_coupon、product_review **也有 order_id 外键**，
#   但它们原来排在 `DELETE FROM orders` 之后 —— 于是删 orders 时被外键拦下
#   （ERROR 1451 fk_wallet_transaction_order），后面的语句全不执行，
#   表现为「脚本跑完了、账号还在」，极容易误判成"删掉了"。
#   所以：**凡是带 order_id 外键的表，必须全部排在 orders 之前**，并额外按 order_id 兜底一遍
#   （user_id 与 order.user_id 不一致的历史脏数据也能清掉）。
echo "--- 执行删除 ---"
q "
DELETE FROM stock_log          WHERE order_id IN (SELECT id FROM orders WHERE user_id=$UID_VAL);
DELETE FROM payment_record     WHERE order_id IN (SELECT id FROM orders WHERE user_id=$UID_VAL);
DELETE FROM return_request     WHERE order_id IN (SELECT id FROM orders WHERE user_id=$UID_VAL);
DELETE FROM wallet_transaction WHERE order_id IN (SELECT id FROM orders WHERE user_id=$UID_VAL);
DELETE FROM user_coupon        WHERE order_id IN (SELECT id FROM orders WHERE user_id=$UID_VAL);
DELETE FROM product_review     WHERE order_id IN (SELECT id FROM orders WHERE user_id=$UID_VAL);
DELETE FROM order_item         WHERE order_id IN (SELECT id FROM orders WHERE user_id=$UID_VAL);
DELETE FROM orders             WHERE user_id=$UID_VAL;
DELETE FROM wallet_transaction WHERE user_id=$UID_VAL;
DELETE FROM point_ledger       WHERE user_id=$UID_VAL;
DELETE FROM cart_item          WHERE user_id=$UID_VAL;
DELETE FROM user_address       WHERE user_id=$UID_VAL;
DELETE FROM user_message       WHERE user_id=$UID_VAL;
DELETE FROM user_favorite      WHERE user_id=$UID_VAL;
DELETE FROM price_alert        WHERE user_id=$UID_VAL;
DELETE FROM user_coupon        WHERE user_id=$UID_VAL;
DELETE FROM product_review     WHERE user_id=$UID_VAL;
DELETE FROM sys_user           WHERE id=$UID_VAL AND username='$NAME';
"

# 4) 事后核对（必须为 0 条 + 无孤儿订单）
echo "--- 核对 ---"
q "SELECT CONCAT('  账号残留=', (SELECT COUNT(*) FROM sys_user WHERE username='$NAME'),
                ' 孤儿订单=', (SELECT COUNT(*) FROM orders o LEFT JOIN sys_user u ON o.user_id=u.id WHERE u.id IS NULL),
                ' 用户总数=', (SELECT COUNT(*) FROM sys_user),
                ' 订单总数=', (SELECT COUNT(*) FROM orders));"

echo
echo "⚠️ 订单里若有已扣库存的商品，本脚本**不会**自动回补库存。"
echo "   需要回补时，请按订单明细逐条执行，并把条件绑定到真实存在的 order_id 上："
echo "     UPDATE product p JOIN order_item oi ON p.id=oi.product_id"
echo "       SET p.stock = p.stock + oi.quantity WHERE oi.order_id = <orderId>;"
