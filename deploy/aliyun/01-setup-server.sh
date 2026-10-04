#!/usr/bin/env bash
# ============================================================
# 超市系统 · 服务器初始化（Alibaba Cloud Linux 3.2104 / RHEL8 系）
# 用法：以 root 在服务器上执行一次
#   bash 01-setup-server.sh
# 前置：把本仓库 deploy/init.sql 传到服务器 /tmp/init.sql
#   （本机 Git Bash： scp deploy/init.sql root@8.218.154.150:/tmp/）
# ============================================================
set -euo pipefail

DB_NAME="supermarket_system"
DB_USER="supermarket"
# ⚠️ 改成你自己的强口令（不要沿用；脚本只用在本机打印，不会传出去）
DB_PASS="${DB_PASS:-$(openssl rand -base64 18)}"
APP_DIR="/opt/supermarket"
WEB_DIR="/var/www/supermarket"
RUN_USER="supermarket"

echo "===== [1/6] 安装运行环境（JDK17 / MySQL8 / nginx）====="
dnf install -y java-17-openjdk-headless mysql-server nginx || {
  echo "安装失败：若提示找不到 mysql-server，请先 dnf makecache 或 dnf install -y mysql-server --enablerepo=*"; exit 1; }
java -version

echo "===== [2/6] 启动 MySQL 并初始化数据库 ====="
systemctl enable --now mysqld
mysqladmin status >/dev/null 2>&1 || { echo "mysqld 未起来"; exit 1; }

# 首次建议跑一次安全初始化（设 root 口令、删匿名用户）；脚本化场景下可手工执行：
#   mysql_secure_installation
if [ ! -f /tmp/init.sql ]; then
  echo "❌ 缺少 /tmp/init.sql，请先 scp deploy/init.sql root@<IP>:/tmp/"; exit 1;
fi
# ⚠️ 带中文必须 utf8mb4（项目约定：否则 ERROR 1366）
mysql --default-character-set=utf8mb4 < /tmp/init.sql
echo "数据库 ${DB_NAME} 建表 + 种子完成"

# ⚠️ 必须同时给 'localhost' / '127.0.0.1' / '%' 授权：
# 后面给 mysqld 加了 skip-name-resolve 后，MySQL 不再把 127.0.0.1 解析成 localhost，
# 而 JDBC 连的正是 127.0.0.1 —— 只授权 localhost 会报
# "Host '127.0.0.1' is not allowed to connect to this MySQL server"（首版部署实测踩过，表现为 /api 全 500）。
mysql --default-character-set=utf8mb4 -e "
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASS}';
CREATE USER IF NOT EXISTS '${DB_USER}'@'127.0.0.1' IDENTIFIED BY '${DB_PASS}';
CREATE USER IF NOT EXISTS '${DB_USER}'@'%' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'localhost';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'127.0.0.1';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'%';
FLUSH PRIVILEGES;"
# 自校验：用应用账号经 127.0.0.1 连一次，连不上就别往下走（否则后面全是 500）
mysql -u "${DB_USER}" -p"${DB_PASS}" -h 127.0.0.1 -e "SELECT 1;" >/dev/null 2>&1 \
  || { echo "❌ 应用账号经 127.0.0.1 连不上库，请检查授权"; exit 1; }

echo "===== [2.5] 小内存兜底：1G swap（2G 规格机器防 OOM）====="
if [ ! -f /swapfile ] && ! swapon --show | grep -q swapfile; then
  fallocate -l 1G /swapfile && chmod 600 /swapfile && mkswap /swapfile >/dev/null && swapon /swapfile
  grep -q "^/swapfile" /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  sysctl -w vm.swappiness=10 >/dev/null
  echo "swap 已启用：$(swapon --show | tail -n 1)"
else
  echo "已有 swap，跳过"
fi

echo "===== [3/6] 创建运行用户与目录 ====="
id -u ${RUN_USER} >/dev/null 2>&1 || useradd -r -s /bin/false ${RUN_USER}
mkdir -p ${APP_DIR}/uploads ${WEB_DIR}
chown -R ${RUN_USER}:${RUN_USER} ${APP_DIR}

echo "===== [4/6] 配置 nginx ====="
cp /tmp/supermarket.conf /etc/nginx/conf.d/supermarket.conf 2>/dev/null || \
  cp "$(dirname "$0")/supermarket.conf" /etc/nginx/conf.d/supermarket.conf
nginx -t && systemctl enable --now nginx

echo "===== [5/6] 防火墙 / SELinux ====="
# 阿里云真正的入口在「安全组」：控制台放行 22 / 80（3306 绝不对公网开）。
# ⚠️ 刻意不去 enable firewalld：本机实测它 inactive，且只放行 http/https 不加 ssh 会把自己锁在门外。
#    仅当它本来就在运行时才补规则（此时 ssh 规则通常已存在）。
if systemctl is-active --quiet firewalld; then
  firewall-cmd --permanent --add-service=http || true
  firewall-cmd --permanent --add-service=https || true
  firewall-cmd --reload || true
  echo "firewalld 运行中，已放行 http/https"
else
  echo "firewalld 未运行 → 跳过（入口由阿里云安全组控制，请在控制台放行 22/80）"
fi

# SELinux：放行 nginx 反向代理到本机 8080，否则访问 /api 会 502
if command -v getenforce >/dev/null && [ "$(getenforce)" = "Enforcing" ]; then
  setsebool -P httpd_can_network_connect 1 || echo "（setsebool 失败，可临时 setenforce 0 排查）"
  # 静态目录打标，否则 nginx 读 dist 会 403
  command -v semanage >/dev/null && \
    { semanage fcontext -a -t httpd_sys_content_t "${WEB_DIR}(/.*)?"; restorecon -Rv ${WEB_DIR}; } || true
fi

echo "===== [6/6] 安装 systemd 服务（先不要启动，等 jar 传上来）====="
cp "$(dirname "$0")/supermarket.service" /etc/systemd/system/supermarket.service
systemctl daemon-reload
systemctl enable supermarket

echo
echo "===== 初始化完成 ====="
echo "数据库账号：${DB_USER} / ${DB_PASS}"
echo "⚠️ 请把上面这个口令填进 /etc/systemd/system/supermarket.service 的 DB_PASSWORD"
echo "   （nano /etc/systemd/system/supermarket.service，改完 systemctl daemon-reload）"
echo "接着回到本机运行： bash deploy/aliyun/publish.sh"
