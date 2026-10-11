#!/usr/bin/env bash
# ============================================================
# 一键迁移：还原数据库 + 还原图片目录 uploads
# 适用：换/新服务器时，把 _backups/ 里的 整库 dump + uploads tar 包 一起还原
# 安全：DB 口令走交互输入（mysql -p 提示），不写死在脚本里
# ============================================================
set -euo pipefail

# 用法：
#   bash migrate.sh                                              # 默认 dump + 默认 uploads 包 + root + supermarket_system
#   bash migrate.sh dump.sql uploads.tar.gz                      # 指定 DB dump 与 uploads 包
#   bash migrate.sh dump.sql uploads.tar.gz myuser mydb          # 全自定义
#
# 环境变量（可选覆盖）：
#   UPLOADS_DIR   图片解压目标目录（默认 /opt/supermarket/uploads）
#   DB_HOST       MySQL 主机（默认 localhost）
DUMP="${1:-supermarket_system_full_20261011_1745.sql}"
UPLOADS_TAR="${2:-supermarket_uploads_20261011_1745.tar.gz}"
DB_USER="${3:-root}"
DB_NAME="${4:-supermarket_system}"
DB_HOST="${DB_HOST:-127.0.0.1}"
UPLOADS_DIR="${UPLOADS_DIR:-/opt/supermarket/uploads}"

if [ ! -f "$DUMP" ]; then
  echo "❌ 找不到 dump 文件: $DUMP" >&2
  exit 1
fi

echo "==> 目标库: $DB_NAME  (用户: $DB_USER @ $DB_HOST)"
echo "==> 来源 dump: $DUMP"
echo "==> 图片目标目录: $UPLOADS_DIR"

# ---------- 1) 还原数据库 ----------
mysql --default-character-set=utf8mb4 -h "$DB_HOST" -u "$DB_USER" -p -e \
  "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

echo "==> 开始还原数据库（按提示输入 $DB_USER 的密码）..."
mysql --default-character-set=utf8mb4 -h "$DB_HOST" -u "$DB_USER" -p "$DB_NAME" < "$DUMP"

echo "✅ 数据库还原完成。"
echo "==> 数据库快速校验："
mysql --default-character-set=utf8mb4 -h "$DB_HOST" -u "$DB_USER" -p "$DB_NAME" -e \
  "SELECT 'product' t, COUNT(*) n FROM product
   UNION ALL SELECT 'orders', COUNT(*) FROM orders
   UNION ALL SELECT 'sys_user', COUNT(*) FROM sys_user
   UNION ALL SELECT 'wallet_transaction', COUNT(*) FROM wallet_transaction;"

# ---------- 2) 还原图片目录 ----------
if [ -f "$UPLOADS_TAR" ]; then
  echo "==> 解压图片包到 $UPLOADS_DIR ..."
  mkdir -p "$UPLOADS_DIR"
  # tar 包内为相对路径（以 ./ 开头），-C 解到目标目录；--skip-old-files 不覆盖已有同名文件
  tar -xzf "$UPLOADS_TAR" -C "$UPLOADS_DIR" --skip-old-files
  echo "✅ 图片还原完成。当前文件数: $(find "$UPLOADS_DIR" -type f | wc -l)"
else
  echo "⚠️  未找到图片包 $UPLOADS_TAR，跳过图片还原（数据库已还原）。"
  echo "    若需要图片，请单独把旧服务器的 /opt/supermarket/uploads 同步到 $UPLOADS_DIR"
fi

echo ""
echo "🎉 迁移完成。记得：部署后端 jar + 前端 dist，并核对 nginx 与证书。"
