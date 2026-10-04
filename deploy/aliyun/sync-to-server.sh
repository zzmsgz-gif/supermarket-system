#!/usr/bin/env bash
# 一次性把「本地开发库 + 上传目录」全量同步到阿里云服务器（server = local 的完整克隆）。
# 用途：部署后想让线上与本地完全一致时跑一次；不是持续同步。
# 风险：会覆盖服务器现有全部数据（用户/订单/后台改动等）。脚本第一步会先备份服务器库到本地，可回滚。
#
# 用法（在本机超市项目根目录执行）：
#   bash deploy/aliyun/sync-to-server.sh
# 前置：ssh 公钥已加入服务器；本机 mysql/mysqldump 在 PATH；本机后端库 supermarket_system 可连。
#
# 如需改连接参数，改下面变量即可。
set -euo pipefail

SERVER="8.218.154.150"
SSH_KEY="$HOME/.ssh/id_ed25519"
SSH="ssh -i $SSH_KEY root@$SERVER"
DB="supermarket_system"

LOCAL_DB_USER="root"
LOCAL_DB_PASS="zzmsgz"            # 改成本机 mysql root 口令
LOCAL_UPLOADS="backend/uploads"   # 本机上传目录（avatar/banner/product）
SERVER_UPLOADS="/opt/supermarket/uploads"
BACKUP_DIR="_dbsync"
TS=$(date +%Y%m%d)

mkdir -p "$BACKUP_DIR"
echo "==> [1/5] 备份服务器库（回滚点）"
$SSH "sudo mysqldump --single-transaction --routines --triggers --default-character-set=utf8mb4 $DB" \
  > "$BACKUP_DIR/server_backup_${TS}.sql"
echo "    已存 $BACKUP_DIR/server_backup_${TS}.sql"

echo "==> [2/5] 导出本地库"
mysqldump -u"$LOCAL_DB_USER" -p"$LOCAL_DB_PASS" --single-transaction --routines --triggers \
  --default-character-set=utf8mb4 --skip-add-drop-database --no-create-db --set-gtid-purged=OFF \
  "$DB" > "$BACKUP_DIR/local_dump.sql"
# 预置关闭外键检查，避免导入顺序报错
{ printf 'SET FOREIGN_KEY_CHECKS=0;\n'; cat "$BACKUP_DIR/local_dump.sql"; } > "$BACKUP_DIR/local_dump.fk.sql"
mv "$BACKUP_DIR/local_dump.fk.sql" "$BACKUP_DIR/local_dump.sql"

echo "==> [3/5] 停后端 -> 重建服务器库 -> 导入 -> 起后端"
$SSH "systemctl stop supermarket \
  && sudo mysql -e \"DROP DATABASE $DB; CREATE DATABASE $DB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\" \
  && sudo mysql --default-character-set=utf8mb4 $DB < /tmp/_sync_dump.sql \
  && systemctl start supermarket"
# 上面用 /tmp/_sync_dump.sql：先把 dump 推上去再导入（避免大管道被中断）
scp -i "$SSH_KEY" "$BACKUP_DIR/local_dump.sql" root@"$SERVER":/tmp/_sync_dump.sql
$SSH "systemctl stop supermarket \
  && sudo mysql -e \"DROP DATABASE $DB; CREATE DATABASE $DB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\" \
  && sudo mysql --default-character-set=utf8mb4 $DB < /tmp/_sync_dump.sql \
  && systemctl start supermarket \
  && rm -f /tmp/_sync_dump.sql"

echo "==> [4/5] 同步上传目录（avatar/banner/product）"
cd "$(dirname "$0")/../.."   # 回到项目根
for d in avatar banner product; do
  [ -d "$LOCAL_UPLOADS/$d" ] || continue
  scp -i "$SSH_KEY" -r "$LOCAL_UPLOADS/$d" root@"$SERVER":"$SERVER_UPLOADS/"
done
$SSH "chown -R supermarket:supermarket $SERVER_UPLOADS"

echo "==> [5/5] 完成。建议用真机打开首页/商品页确认；并记得服务器后台管理员口令已变成本地那条。"
