#!/usr/bin/env bash
# ============================================================
# 超市系统 - 本机一键发布到阿里云（Alibaba Cloud Linux 3）
# 在本机 Git Bash 运行： bash deploy/aliyun/publish.sh
#
# 前置：服务器已跑过 deploy/aliyun/01-setup-server.sh，且能 ssh root@8.218.154.150
# 作用：本机构建 jar + 前端 dist → scp 上传 → 重启 systemd 服务
# ============================================================
set -euo pipefail

# ===================== 按你的环境修改 =====================
SERVER_HOST="root@8.218.154.150"
REMOTE_APP_DIR="/opt/supermarket"        # 后端 jar + uploads
REMOTE_WEB_DIR="/var/www/supermarket"    # 前端静态目录（nginx root）
RUN_USER="supermarket"
# =========================================================

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

echo "==> [1/4] 构建后端 jar"
cd backend
JAVA_HOME="E:/java/develope" "D:/develop/apache-maven-3.9.9/bin/mvn.cmd" -q package -DskipTests
cd "$ROOT"

echo "==> [2/4] 构建前端 dist"
cd frontend
NODE_OPTIONS= npx vite build   # 默认 emptyOutDir=true：本机 dist 每次清空重建，不残留旧 chunk，无需线上 rm
cd "$ROOT"

echo "==> [3/4] 上传 jar + 前端静态文件到 ${SERVER_HOST}"
ssh "$SERVER_HOST" "mkdir -p ${REMOTE_APP_DIR}/uploads ${REMOTE_WEB_DIR} && chown -R ${RUN_USER}:${RUN_USER} ${REMOTE_APP_DIR}"
# 发布前在服务器留一份旧 jar 便于回滚
ssh "$SERVER_HOST" "[ -f ${REMOTE_APP_DIR}/app.jar ] && cp ${REMOTE_APP_DIR}/app.jar ${REMOTE_APP_DIR}/app.jar.bak || true"
scp backend/target/*.jar "$SERVER_HOST:${REMOTE_APP_DIR}/app.jar"
# 前端原子发布：先传到独立的临时目录，再在同文件系统内 rename 一次性切换，
# 彻底消除「rm -rf 清空 → scp 几百个文件慢慢传」之间的全站空窗期（那段时间内访客会刷到裂图/白屏）。
# rename 在同分区（/var/www）是瞬时原子操作，线上始终服务完整目录，访客零感知。
# 切换前的旧目录保留为 supermarket_bak 便于回滚。
STAGE_DIR="${REMOTE_WEB_DIR}_stage"
ssh "$SERVER_HOST" "rm -rf ${STAGE_DIR} && mkdir -p ${STAGE_DIR} && chown -R ${RUN_USER}:${RUN_USER} ${STAGE_DIR}"
scp -r frontend/dist/* "$SERVER_HOST:${STAGE_DIR}/"

echo "==> [3.5/4] 发布前校验图片引用：库里每个图片 URL 必须在即将上线的目录中真实存在"
echo "    （说明：商品图 URL 含扩展名硬编码在库，曾因 .webp/.jpg 不一致导致线上裂图；"
echo "     此步在切量前卡死这类不一致，发现即中止发布。需 prod DB 密码，"
echo "     运行前请 export SUPERMARKET_DB_PASS='<prod密码>'；未设置则跳过本步。）"
ssh "$SERVER_HOST" STAGE_DIR="$STAGE_DIR" MYPASS="$SUPERMARKET_DB_PASS" bash -s <<'VERIFY'
set -e
if [ -z "$MYPASS" ]; then
  echo "⚠️ 未设置 SUPERMARKET_DB_PASS，跳过图片引用校验（建议设置以启用发布前裂图防护）。"
  exit 0
fi
TMP=$(mktemp)
for pair in "product:cover_url" "banner:image_url" "category:image_url" "flash_sale:image_url" "activity:image_url" "carousel:image_url" "announcement:image_url"; do
  tbl=${pair%%:*}; col=${pair##*:}
  mysql -usupermarket -p"$MYPASS" supermarket_system -N -e "SELECT $col FROM $tbl WHERE $col IS NOT NULL AND $col != ''" 2>/dev/null | while IFS= read -r u; do
    if echo "$u" | grep -q '^/seed'; then f="$STAGE_DIR$u";
    elif echo "$u" | grep -q '^/api/uploads'; then f="/opt/supermarket${u#/api}";
    elif echo "$u" | grep -q '^/uploads'; then f="/opt/supermarket$u"; else f=""; fi
    if [ -n "$f" ] && [ ! -f "$f" ]; then echo "MISSING: $u -> $f"; fi
  done >> "$TMP"
done
if [ -s "$TMP" ]; then
  echo "❌ 发布前校验失败：以下图片在即将上线的目录中缺失，已中止发布（避免裂图上线）："
  cat "$TMP"
  rm -f "$TMP"
  exit 1
fi
rm -f "$TMP"
echo "✅ 图片引用校验通过：数据库全部图片 URL 均能在即将上线的目录中找到对应文件。"
VERIFY

ssh "$SERVER_HOST" "rm -rf ${REMOTE_WEB_DIR}_bak && mv ${REMOTE_WEB_DIR} ${REMOTE_WEB_DIR}_bak && mv ${STAGE_DIR} ${REMOTE_WEB_DIR} && chown -R ${RUN_USER}:${RUN_USER} ${REMOTE_WEB_DIR}"

echo "==> [4/4] 重启后端服务"
ssh "$SERVER_HOST" "systemctl daemon-reload && systemctl restart supermarket && sleep 6 && systemctl status supermarket --no-pager | head -n 10"

echo
echo "==> 发布完成。浏览器访问 http://${SERVER_HOST#root@}"
echo "    排查日志： ssh ${SERVER_HOST} 'journalctl -u supermarket -n 100 --no-pager'"
