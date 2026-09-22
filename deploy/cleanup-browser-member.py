"""清理 setup-browser-member.py 造出的浏览器演示账号（含其可能产生的全部痕迹）。

用法：python deploy/cleanup-browser-member.py <uid>

背景：setup-browser-member.py 的 docstring 引用了本脚本，但此前一直不存在，
导致每次浏览器演示都会残留一个用户 + 新人券 + 积分流水。此脚本补齐该缺口。

清理顺序遵循「环形外键」约定（orders.user_coupon_id <-> user_coupon.order_id 成环，
直接删任一侧报 ERROR 1451）：
  1. 还原库存（排除已取消/已关闭订单——它们已通过取消流程回退过，再退一次会凭空多库存）
  2. UPDATE orders SET user_coupon_id=NULL 断环
  3. 删 user 级流水（wallet_transaction 的 FK 指向 orders，必须最先）
  4. 删 order_item / payment_record / stock_log
  5. 删 orders
  6. 删 sys_user
  7. 重算 flash_sale.sold_quota（手工删订单不会触发回退）
  8. 残留自检
"""
import subprocess
import sys
from _db import DBUSER, DBPASS

MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
DB = "supermarket_system"

if len(sys.argv) < 2:
    print("usage: python deploy/cleanup-browser-member.py <uid>")
    sys.exit(2)
uid = sys.argv[1].strip()


def sql(stmt):
    p = subprocess.run([MYSQL, "-u", DBUSER, "-p" + DBPASS, "-D", DB,
                        "--default-character-set=utf8mb4", "-N", "-B", "-e", stmt],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if p.returncode != 0:
        raise RuntimeError("SQL fail: " + p.stderr.strip() + "\n  stmt: " + stmt[:200])
    return p.stdout.strip()


def count(table, where):
    out = sql(f"SELECT COUNT(*) FROM {DB}.{table} WHERE {where}")
    return int(out or 0)


# 0. 确认目标存在（避免误删不存在的 id 却报 OK）
exists = count("sys_user", f"id={uid}")
if not exists:
    print(f"SKIP: sys_user id={uid} 不存在（可能已清理过）")
    sys.exit(0)

# 1. 还原库存：只针对「未曾回退过」的订单（已取消/已关闭订单跳过）
rows = sql(
    f"SELECT oi.product_id, SUM(oi.quantity) FROM {DB}.order_item oi "
    f"JOIN {DB}.orders o ON o.id = oi.order_id "
    f"WHERE o.user_id={uid} AND o.status NOT IN ('CANCELED','CLOSED') "
    f"GROUP BY oi.product_id")
restored = 0
for line in [l for l in rows.splitlines() if l.strip()]:
    pid, qty = line.split("\t")
    sql(f"UPDATE {DB}.product SET stock=stock+{int(qty)}, "
        f"sales=GREATEST(sales-{int(qty)},0) WHERE id={int(pid)}")
    restored += 1

# 2. 断环，让 user_coupon 可删
sql(f"UPDATE {DB}.orders SET user_coupon_id=NULL WHERE user_id={uid}")

# 3. user 级流水 —— wallet_transaction 的 FK 指向 orders，必须排在最前
# ⚠️ product_review 必须在这里删 —— 它有两条外键（order_id→orders、order_item_id→order_item），
# 漏掉它会让下面删 order_item 时直接 ERROR 1451，整个清理中途失败并留下脏数据。
# page_dwell（商品停留埋点）也指向 sys_user —— 只有浏览器会话会产生它，所以只有本脚本会踩到。
for t in ("product_review", "page_dwell", "wallet_transaction", "user_message", "point_ledger",
          "user_favorite", "price_alert", "cart_item", "user_address", "user_coupon"):
    sql(f"DELETE FROM {DB}.{t} WHERE user_id={uid}")

# 4~5. 订单子表 → orders
sql(f"DELETE oi FROM {DB}.order_item oi JOIN {DB}.orders o ON o.id=oi.order_id "
    f"WHERE o.user_id={uid}")
sql(f"DELETE pr FROM {DB}.payment_record pr JOIN {DB}.orders o ON o.id=pr.order_id "
    f"WHERE o.user_id={uid}")
sql(f"DELETE sl FROM {DB}.stock_log sl JOIN {DB}.orders o ON o.id=sl.order_id "
    f"WHERE o.user_id={uid}")
sql(f"DELETE FROM {DB}.orders WHERE user_id={uid}")

# 6. 用户
sql(f"DELETE FROM {DB}.sys_user WHERE id={uid}")

# 7. 秒杀名额重算：手工删订单不会触发回退，否则场次永远停显「已抢 N 件」
sql(f"UPDATE {DB}.flash_sale fs SET fs.sold_quota=("
    f"  SELECT COALESCE(SUM(oi.quantity),0) FROM {DB}.order_item oi "
    f"  JOIN {DB}.orders o ON o.id=oi.order_id "
    f"  WHERE oi.flash_sale_id=fs.id AND o.status NOT IN ('CANCELED','CLOSED'))")

# 8. 残留自检
leftovers = []
for t in ("sys_user", "orders", "cart_item", "user_address", "user_coupon",
          "point_ledger", "user_message", "user_favorite", "price_alert",
          "wallet_transaction"):
    c = count(t, f"user_id={uid}") if t != "sys_user" else count("sys_user", f"id={uid}")
    if c:
        leftovers.append(f"{t}={c}")

print(f"CLEANUP uid={uid} 还原库存的商品数={restored}")
print("CLEANUP_OK" if not leftovers else "CLEANUP_LEFTOVER: " + ", ".join(leftovers))
