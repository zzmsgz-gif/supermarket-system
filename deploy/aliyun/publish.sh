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
NODE_OPTIONS= npx vite build --emptyOutDir false
cd "$ROOT"

echo "==> [3/4] 上传 jar + 前端静态文件到 ${SERVER_HOST}"
ssh "$SERVER_HOST" "mkdir -p ${REMOTE_APP_DIR}/uploads ${REMOTE_WEB_DIR} && chown -R ${RUN_USER}:${RUN_USER} ${REMOTE_APP_DIR}"
# 发布前在服务器留一份旧 jar 便于回滚
ssh "$SERVER_HOST" "[ -f ${REMOTE_APP_DIR}/app.jar ] && cp ${REMOTE_APP_DIR}/app.jar ${REMOTE_APP_DIR}/app.jar.bak || true"
scp backend/target/*.jar "$SERVER_HOST:${REMOTE_APP_DIR}/app.jar"
# 清空旧静态文件后整体覆盖，避免残留旧 chunk（--emptyOutDir false 会在本机留下孤儿文件）
ssh "$SERVER_HOST" "rm -rf ${REMOTE_WEB_DIR}/* || true"
scp -r frontend/dist/* "$SERVER_HOST:${REMOTE_WEB_DIR}/"

echo "==> [4/4] 重启后端服务"
ssh "$SERVER_HOST" "systemctl daemon-reload && systemctl restart supermarket && sleep 6 && systemctl status supermarket --no-pager | head -n 10"

echo
echo "==> 发布完成。浏览器访问 http://${SERVER_HOST#root@}"
echo "    排查日志： ssh ${SERVER_HOST} 'journalctl -u supermarket -n 100 --no-pager'"
