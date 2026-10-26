#!/usr/bin/env bash
# ============================================================
# 生产服务器端【幂等迁移器】：自动从 supermarket 的 systemd 单元读取
# DB 账号密码（密码不离开服务器、不经外部传输），先备份受影响三表，
# 再只应用尚未落地的迁移。可重复运行。
#
# 用法（本机 Git Bash）：
#   ssh root@8.218.154.150 'bash -s' < deploy/aliyun/apply-migrations-server.sh
#
# 覆盖 deploy/ 下当前全部待上线迁移（条件式，缺什么补什么）：
#   freeze-points / refund-status / drop-freight / wallet-points-deducted / reconcile-frozen
# ============================================================
set -euo pipefail

DB="supermarket_system"
ENVLINE="$(systemctl show supermarket -p Environment | sed 's/^Environment=//')"
DB_USER="$(printf '%s\n' "$ENVLINE" | tr ' ' '\n' | sed -n 's/^DB_USERNAME=//p' | head -1)"
DB_PASS="$(printf '%s\n' "$ENVLINE" | tr ' ' '\n' | sed -n 's/^DB_PASSWORD=//p' | head -1)"
if [ -z "$DB_USER" ] || [ -z "$DB_PASS" ]; then echo "❌ 无法从 systemd 读取 DB_USERNAME/DB_PASSWORD"; exit 1; fi
echo "DB_USER=$DB_USER  密码长度=${#DB_PASS}"

run() { mysql -u"$DB_USER" -p"$DB_PASS" "$DB" -e "$1"; }
col_exists() { mysql -u"$DB_USER" -p"$DB_PASS" "$DB" -N -e "SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB' AND TABLE_NAME='$1' AND COLUMN_NAME='$2' LIMIT 1" 2>/dev/null | grep -q 1; }
chk_exists() { mysql -u"$DB_USER" -p"$DB_PASS" "$DB" -N -e "SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='$DB' AND TABLE_NAME='$1' AND CONSTRAINT_NAME='$2' LIMIT 1" 2>/dev/null | grep -q 1; }

TS="$(date +%Y%m%d-%H%M%S)"
echo "=== 条件式迁移开始 ($(date)) ==="
mkdir -p /opt/supermarket
mysqldump -u"$DB_USER" -p"$DB_PASS" "$DB" sys_user return_request wallet_transaction > "/opt/supermarket/backup-$TS.sql" 2>/dev/null
echo "[backup] /opt/supermarket/backup-$TS.sql ($(ls -lh "/opt/supermarket/backup-$TS.sql" | awk '{print $5}'))"

# ---------- 1) freeze-points（方案D 冻结分列）----------
echo "--- [1/5] freeze-points ---"
if col_exists sys_user frozen_points; then echo "[skip] sys_user.frozen_points"; else run "ALTER TABLE sys_user ADD COLUMN frozen_points BIGINT NOT NULL DEFAULT 0;"; echo "[apply] +sys_user.frozen_points"; fi
if col_exists return_request frozen_points; then echo "[skip] return_request.frozen_points"; else run "ALTER TABLE return_request ADD COLUMN frozen_points BIGINT NOT NULL DEFAULT 0;"; echo "[apply] +return_request.frozen_points"; fi
if col_exists return_request points_deducted; then echo "[skip] return_request.points_deducted"; else run "ALTER TABLE return_request ADD COLUMN points_deducted BIGINT NOT NULL DEFAULT 0;"; echo "[apply] +return_request.points_deducted"; fi

# ---------- 2) refund-status（约束扩展到 7 值）----------
echo "--- [2/5] refund-status ---"
if chk_exists orders chk_orders_refund_status; then run "ALTER TABLE orders DROP CONSTRAINT chk_orders_refund_status;"; echo "[apply] drop old chk_orders_refund_status"; fi
run "ALTER TABLE orders ADD CONSTRAINT chk_orders_refund_status CHECK (refund_status IN ('NONE','APPLYING','APPROVED','REJECTED','WAITING_SHIP','SHIPPED_BACK','PARTIAL'));"
echo "[apply] chk_orders_refund_status (7 values)"

# ---------- 3) drop-freight（清理 freight_borne_by）----------
echo "--- [3/5] drop-freight ---"
if col_exists return_request freight_borne_by; then
  if chk_exists return_request chk_return_freight; then run "ALTER TABLE return_request DROP CHECK chk_return_freight;"; echo "[apply] drop chk_return_freight"; fi
  run "ALTER TABLE return_request DROP COLUMN freight_borne_by;"
  echo "[apply] drop return_request.freight_borne_by"
else
  echo "[skip] return_request.freight_borne_by 已不存在"
fi

# ---------- 4) wallet-points-deducted（#713）----------
echo "--- [4/5] wallet-points-deducted ---"
if col_exists wallet_transaction points_deducted; then
  echo "[skip] wallet_transaction.points_deducted"
else
  run "ALTER TABLE wallet_transaction ADD COLUMN points_deducted DECIMAL(10,2) DEFAULT NULL COMMENT '退款时已花积分等价扣回金额（元）；仅退款且发生积分扣回时有值';"
  echo "[apply] +wallet_transaction.points_deducted"
fi

# ---------- 5) reconcile-frozen（#712 数据对账，幂等）----------
echo "--- [5/5] reconcile-frozen ---"
run "UPDATE sys_user u JOIN (SELECT r.user_id, SUM(COALESCE(r.frozen_points,0)) AS fp FROM return_request r WHERE r.status IN ('APPLYING','WAITING_SHIP','SHIPPED_BACK') GROUP BY r.user_id) s ON s.user_id=u.id SET u.frozen_points=s.fp;"
run "UPDATE sys_user SET frozen_points=0 WHERE id NOT IN (SELECT DISTINCT user_id FROM return_request WHERE status IN ('APPLYING','WAITING_SHIP','SHIPPED_BACK'));"
echo "[apply] 已按未结售后单重算 sys_user.frozen_points"

echo "=== 条件式迁移完成 ($(date)) ==="
echo "备份：/opt/supermarket/backup-$TS.sql"
