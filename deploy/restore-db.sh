#!/usr/bin/env bash
# ============================================================
# 方案 A：直接还原完整 mysqldump（全站数据库）
# 适用：换/新服务器时，把 _backups/ 里的整库 dump 还原过去
# 安全：口令走交互输入（mysql -p 提示），不写死在脚本里
# ============================================================
set -euo pipefail

# 用法：
#   bash restore-db.sh                         # 用默认 dump + root + supermarket_system
#   bash restore-db.sh /path/to/dump.sql       # 指定 dump
#   bash restore-db.sh dump.sql myuser mydb    # 全自定义
DUMP="${1:-supermarket_system_full_20261011_142538.sql}"
DB_USER="${2:-root}"
DB_NAME="${3:-supermarket_system}"

if [ ! -f "$DUMP" ]; then
  echo "❌ 找不到 dump 文件: $DUMP" >&2
  exit 1
fi

echo "==> 目标库: $DB_NAME  (用户: $DB_USER)"
echo "==> 来源 dump: $DUMP"

# 1) 建库（若不存在）。中文必须 utf8mb4，否则 emoji/中文乱码
mysql --default-character-set=utf8mb4 -u "$DB_USER" -p -e \
  "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2) 还原整库（建表 + 数据）。务必带 --default-character-set=utf8mb4
echo "==> 开始还原（按提示输入 $DB_USER 的密码）..."
mysql --default-character-set=utf8mb4 -u "$DB_USER" -p "$DB_NAME" < "$DUMP"

echo "✅ 还原完成。"
echo "==> 快速校验："
mysql --default-character-set=utf8mb4 -u "$DB_USER" -p "$DB_NAME" -e \
  "SELECT 'product' t, COUNT(*) n FROM product
   UNION ALL SELECT 'orders', COUNT(*) FROM orders
   UNION ALL SELECT 'sys_user', COUNT(*) FROM sys_user
   UNION ALL SELECT 'wallet_transaction', COUNT(*) FROM wallet_transaction;"
