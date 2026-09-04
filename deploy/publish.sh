#!/usr/bin/env bash
# 超市系统 - 本地一键发布到服务器（手动发布脚本）
#
# 前置：
#   1) 服务器已按 deploy/server-setup.md 完成初始化（JDK17 / MySQL8 / nginx / 建库 / 服务文件）。
#   2) 本机已装 Maven + JDK17 + Node，且能用 ssh 免密（或口令）登录服务器。
#   3) 在 Git Bash 中运行本脚本：  bash deploy/publish.sh
#
# 设计：服务器跑的是「发布产物」（jar + dist），与你的本地开发目录完全隔离。
#       平时本地随便改、本地 8080/5173 跑；想让别人看到新版本时，本地自测 OK 后跑一次本脚本即可。
set -euo pipefail

# ===================== 按你的环境修改 =====================
SERVER_HOST="root@YOUR_SERVER_IP"        # 改成服务器 IP（建议用专用部署用户，如 deploy@IP）
REMOTE_APP_DIR="/opt/supermarket"        # 后端 jar 目录（与 .service 中一致）
REMOTE_WEB_DIR="/var/www/supermarket"    # 前端静态目录（与 nginx-site.conf 中一致）
RUN_USER="supermarket"                   # 运行后端服务的系统用户（与 .service 中一致）
# =========================================================

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "==> [1/4] 构建后端 jar"
cd backend
JAVA_HOME="E:/java/develope" "D:/develop/apache-maven-3.9.9/bin/mvn.cmd" -q package -DskipTests
cd "$ROOT"

echo "==> [2/4] 构建前端 dist"
cd frontend
npx vite build --emptyOutDir false
cd "$ROOT"

echo "==> [3/4] 上传 jar + 前端静态文件到 $SERVER_HOST"
ssh "$SERVER_HOST" "sudo mkdir -p $REMOTE_APP_DIR $REMOTE_WEB_DIR && sudo chown -R $RUN_USER:$RUN_USER $REMOTE_APP_DIR"
scp backend/target/*.jar "$SERVER_HOST:$REMOTE_APP_DIR/app.jar"
# 清空旧静态文件后整体覆盖，避免残留旧 chunk
ssh "$SERVER_HOST" "sudo rm -rf $REMOTE_WEB_DIR/* && sudo chown -R $RUN_USER:$RUN_USER $REMOTE_WEB_DIR"
scp -r frontend/dist/* "$SERVER_HOST:$REMOTE_WEB_DIR/"

echo "==> [4/4] 重启后端服务"
ssh "$SERVER_HOST" "sudo systemctl restart supermarket && sleep 2 && sudo systemctl status supermarket --no-pager | head -n 8"

echo "==> 发布完成。浏览器访问 http://$SERVER_HOST"
