#!/usr/bin/env bash
# ============================================================
# 条件式（幂等）迁移：先 introspect prod schema，只应用尚未落地的部分。
# 可重复运行，不会因「列/约束已存在」而失败。
#
# 前置：export SUPERMARKET_DB_PASS='<prod密码>'，且能 ssh root@8.218.154.150
# 用法：bash deploy/aliyun/apply-migrations.sh
#
# 覆盖本仓库当前所有未提交/待上线迁移：
#   upgrade-2026-10-21-freeze-points.sql      方案D 冻结分列（已上线则自动跳过）
#   upgrade-2026-10-21-refund-status.sql      扩展 refund_status 检查约束到 7 值
#   upgrade-2026-10-22-drop-freight.sql       清理 return_request.freight_borne_by
#   upgrade-2026-10-23-wallet-points-deducted.sql  #713 钱包流水扣回列（本次新）
#   upgrade-2026-10-23-reconcile-frozen.sql     #712 解冻脏数据（幂等 UPDATE）
# ============================================================
set -euo pipefail

SERVER_HOST="root@8.218.154.150"
DB="supermarket_system"
MYPASS="${SUPERMARKET_DB_PASS:?请先 export SUPERMARKET_DB_PASS='<prod密码>'}"
TS="$(date +%Y%m%d-%H%M%S)"

ssh "$SERVER_HOST" "MYPASS=$MYPASS TS=$TS" bash -s <<'REMOTE'
set -e
DB="supermarket_system"
MYPASS="$MYPASS"

run() { mysql -usupermarket -p"$MYPASS" "$DB" -e "$1"; }
col_exists() {
  mysql -usupermarket -p"$MYPASS" "$DB" -N -e \
    "SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB' AND TABLE_NAME='$1' AND COLUMN_NAME='$2' LIMIT 1" 2>/dev/null \
    | grep -q 1
}
chk_exists() {
  mysql -usupermarket -p"$MYPASS" "$DB" -N -e \
    "SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='$DB' AND TABLE_NAME='$1' AND CONSTRAINT_NAME='$2' LIMIT 1" 2>/dev/null \
    | grep -q 1
}

echo "=== 条件式迁移开始 ($(date)) ==="

# ---------- 0) 备份受影响表（幂等，可重复）----------
echo "[backup] 导出 sys_user / return_request / wallet_transaction 到 /opt/supermarket/backup-${TS}.sql"
mkdir -p /opt/supermarket
mysqldump -usupermarket -p"$MYPASS" "$DB" sys_user return_request wallet_transaction \
  > "/opt/supermarket/backup-${TS}.sql" 2>/dev/null
echo "[backup] 完成：$(ls -lh /opt/supermarket/backup-${TS}.sql | awk '{print $5}')"

# ---------- 1) freeze-points (2026-10-21 方案D) ----------
echo "--- [1/5] freeze-points ---"
if col_exists sys_user frozen_points; then echo "[skip] sys_user.frozen_points 已存在"; else run "ALTER TABLE sys_user ADD COLUMN frozen_points BIGINT NOT NULL DEFAULT 0;"; echo "[apply] +sys_user.frozen_points"; fi
if col_exists return_request frozen_points; then echo "[skip] return_request.frozen_points 已存在"; else run "ALTER TABLE return_request ADD COLUMN frozen_points BIGINT NOT NULL DEFAULT 0;"; echo "[apply] +return_request.frozen_points"; fi
if col_exists return_request points_deducted; then echo "[skip] return_request.points_deducted 已存在"; else run "ALTER TABLE return_request ADD COLUMN points_deducted BIGINT NOT NULL DEFAULT 0;"; echo "[apply] +return_request.points_deducted"; fi

# ---------- 2) refund-status (2026-10-21) ----------
echo "--- [2/5] refund-status ---"
# 约束无论当前是否放行全部 7 值，都重建为完整版本（幂等：先删后建；不存在则直接建）
if chk_exists orders chk_orders_refund_status; then
  run "ALTER TABLE orders DROP CONSTRAINT chk_orders_refund_status;"
  echo "[apply] 删除旧 chk_orders_refund_status"
fi
run "ALTER TABLE orders ADD CONSTRAINT chk_orders_refund_status CHECK (refund_status IN ('NONE','APPLYING','APPROVED','REJECTED','WAITING_SHIP','SHIPPED_BACK','PARTIAL'));"
echo "[apply] 重建 chk_orders_refund_status (7 值)"

# ---------- 3) drop-freight (2026-10-22) ----------
echo "--- [3/5] drop-freight ---"
if col_exists return_request freight_borne_by; then
  if chk_exists return_request chk_return_freight; then run "ALTER TABLE return_request DROP CHECK chk_return_freight;"; echo "[apply] 删除 chk_return_freight"; fi
  run "ALTER TABLE return_request DROP COLUMN freight_borne_by;"
  echo "[apply] 删除 return_request.freight_borne_by"
else
  echo "[skip] return_request.freight_borne_by 已不存在"
fi

# ---------- 4) wallet-points-deducted (2026-10-23 #713) ----------
echo "--- [4/5] wallet-points-deducted ---"
if col_exists wallet_transaction points_deducted; then
  echo "[skip] wallet_transaction.points_deducted 已存在"
else
  run "ALTER TABLE wallet_transaction ADD COLUMN points_deducted DECIMAL(10,2) DEFAULT NULL COMMENT '退款时已花积分等价扣回金额（元）；仅退款且发生积分扣回时有值';"
  echo "[apply] +wallet_transaction.points_deducted"
fi

# ---------- 5) reconcile-frozen (2026-10-23 #712) ----------
echo "--- [5/5] reconcile-frozen (幂等 UPDATE) ---"
run "UPDATE sys_user u JOIN (SELECT r.user_id, SUM(COALESCE(r.frozen_points,0)) AS fp FROM return_request r WHERE r.status IN ('APPLYING','WAITING_SHIP','SHIPPED_BACK') GROUP BY r.user_id) s ON s.user_id=u.id SET u.frozen_points=s.fp;"
run "UPDATE sys_user SET frozen_points=0 WHERE id NOT IN (SELECT DISTINCT user_id FROM return_request WHERE status IN ('APPLYING','WAITING_SHIP','SHIPPED_BACK'));"
echo "[apply] 已按未结售后单重算 sys_user.frozen_points"

echo "=== 条件式迁移完成 ($(date)) ==="
echo "备份文件：/opt/supermarket/backup-${TS}.sql"
REMOTE
