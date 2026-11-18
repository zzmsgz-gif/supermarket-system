#!/usr/bin/env bash
# ============================================================
# 超市系统 - 本机一键发布到阿里云（Alibaba Cloud Linux 3）
# 在本机 Git Bash 运行： bash deploy/aliyun/publish.sh
#
# 前置：服务器已跑过 deploy/aliyun/01-setup-server.sh，且能 ssh root@8.218.154.150
# 作用：本机构建 jar + 前端 dist → 上传校验 → 原子切换 → 重启 systemd 服务
#
# ⚠️ 停机原则（2026-10-28 / 2026-11-18 两次事故的经验）：
#   ① **绝不在服务运行时覆盖 app.jar**。运行中的 JVM 会按需从 jar 里读类，
#      覆盖瞬间读到被截断的 ZIP 就会 NoClassDefFound，整站 /api 卡死。
#      → 本脚本一律先传成 app.jar.new、校验通过，最后才 stop → mv → start。
#   ② **stop 与 start 必须成对**。中途失败若停在"已 stop"，站点就是 502。
#      → 本脚本用 trap 兜底：一旦进入停机阶段后出错，自动把服务拉起来。
#   ③ 停机窗口只包含 `mv`(rename，瞬时) + `systemctl start`，通常 3~5 秒；
#      耗时的 scp 上传、图片对账、前端切换全部放在停机之前完成。
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

REMOTE_JAR="${REMOTE_APP_DIR}/app.jar"
REMOTE_JAR_NEW="${REMOTE_APP_DIR}/app.jar.new"
STAGE_DIR="${REMOTE_WEB_DIR}_stage"
BACKUP_WEB_DIR="${REMOTE_WEB_DIR}_bak"

# ---- 停机兜底：进入 [5/5] 停机阶段后若任何一步失败，自动把服务拉起来 ----
# 不做这个的话，`set -e` 会让脚本在 stop 之后直接退出，线上就停在 502 了。
STOPPED=0
recover_on_fail() {
  local code=$?
  if [ "$code" -ne 0 ] && [ "$STOPPED" = "1" ]; then
    echo ""
    echo "⚠️ 发布中断（exit=$code），正在把服务恢复运行..."
    ssh "$SERVER_HOST" "systemctl start supermarket" || true
    echo "   已尝试 systemctl start supermarket。若仍不可用，用备份 jar 回滚："
    echo "   ssh $SERVER_HOST 'cd ${REMOTE_APP_DIR} && mv app.jar app.jar.failed && cp app.jar.bak app.jar && systemctl restart supermarket'"
  fi
}
trap recover_on_fail EXIT

echo "==> [1/5] 构建后端 jar"
cd backend
JAVA_HOME="E:/java/develope" "D:/develop/apache-maven-3.9.9/bin/mvn.cmd" -q clean package -DskipTests
cd "$ROOT"

echo "==> [2/5] 构建前端 dist"
cd frontend
NODE_OPTIONS= npx vite build   # 默认 emptyOutDir=true：本机 dist 每次清空重建，不残留旧 chunk
cd "$ROOT"

echo "==> [3/5] 上传 jar（传成 app.jar.new，绝不碰运行中的 app.jar）"
ssh "$SERVER_HOST" "mkdir -p ${REMOTE_APP_DIR}/uploads ${REMOTE_WEB_DIR} && chown -R ${RUN_USER}:${RUN_USER} ${REMOTE_APP_DIR}"
scp backend/target/*.jar "$SERVER_HOST:${REMOTE_JAR_NEW}"

# ---- 上传后校验 jar 完整性：防 scp 传输出错 / 文件损坏导致「重启即崩」 ----
# 2026-10-28 事故：某次发布 scp 把 jar 传坏（ZIP 在 AnnouncementRequest.class 处断裂），
# 但发布脚本无校验，紧接着 systemctl restart 直接让生产崩循环、整站 8080 全掉。
# 故此处比对 sha256 + zip 无损，任一项不符立即中止（此时服务仍在跑旧 jar，无影响）。
LOCAL_JAR=$(ls backend/target/*.jar | head -n1)
LOCAL_SHA=$(sha256sum "$LOCAL_JAR" | awk '{print $1}')
echo "==> [3.1/5] 校验新 jar 完整性（sha256 一致 + zip 无损）"
SERVER_OUT=$(ssh "$SERVER_HOST" REMOTE_APP="${REMOTE_JAR_NEW}" 'python3 - <<PY
import zipfile, hashlib, os, sys
p=os.environ["REMOTE_APP"]
try:
    z=zipfile.ZipFile(p); bad=z.testzip()
except Exception as e:
    print("ZIPERR", e); sys.exit(0)
print("ZIPOK" if bad is None else "CORRUPT")
print(hashlib.sha256(open(p,"rb").read()).hexdigest())
PY')
SERVER_SHA=$(echo "$SERVER_OUT" | grep -E '^[0-9a-f]{64}$' || true)
if ! echo "$SERVER_OUT" | grep -q '^ZIPOK' || [ "$SERVER_SHA" != "$LOCAL_SHA" ]; then
  echo "❌ jar 校验失败！状态=$(echo "$SERVER_OUT" | grep -E '^(ZIPOK|CORRUPT|ZIPERR)' || echo '?') 本地sha=$LOCAL_SHA 服务器sha=${SERVER_SHA:-无}"
  ssh "$SERVER_HOST" "rm -f ${REMOTE_JAR_NEW}" || true
  echo "   已中止发布。服务仍在运行旧 jar，未受影响。请检查网络后重试。"
  exit 1
fi
echo "✅ jar 完整性校验通过（sha256 一致、zip 无损）。"

echo "==> [4/5] 前端：先在本机无关的独立目录准备，再原子切换（不停机）"
# ⚠️ 不能"只传 dist"：线上目录里可能存在**只存在于服务器**的文件
# （历史上就有几批 webp 只放服务器、dist 里是同名 jpg），dist-only 发布会把它们删成裂图。
# 所以先把现网整目录复制一份到 stage，再把新 dist **覆盖**上去（不 delete）。
# 用 cp -a（真实复制）而不是 cp -al（硬链）：硬链下 scp 覆盖会 truncate 同一个 inode，
# 等于直接改到现网文件；这里只有两百多个文件，多花不到 1 秒，不值得为省这点时间冒险。
ssh "$SERVER_HOST" "rm -rf ${STAGE_DIR} && cp -a ${REMOTE_WEB_DIR} ${STAGE_DIR} && chown -R ${RUN_USER}:${RUN_USER} ${STAGE_DIR}"
scp -r frontend/dist/* "$SERVER_HOST:${STAGE_DIR}/"

echo "==> [4.1/5] 发布前校验图片引用：库里每个图片 URL 必须在即将上线的目录中真实存在"
echo "    （商品图 URL 含扩展名硬编码在库，曾因 .webp/.jpg 不一致导致线上裂图；"
echo "     此步在切量前卡死这类不一致，发现即中止发布。）"
# 认证方式：优先用服务器 root 走 socket 免密（已验证可用），这样**不需要 DB 密码**也能做防护；
# root 不通时才回退到 supermarket 账号 + SUPERMARKET_DB_PASS。旧版"没密码就整步跳过"等于没防护。
ssh "$SERVER_HOST" STAGE_DIR="$STAGE_DIR" MYPASS="${SUPERMARKET_DB_PASS:-}" bash -s <<'VERIFY'
set -eu
ROOT_OK=0
if mysql -uroot supermarket_system -N -B -e "SELECT 1" >/dev/null 2>&1; then
  ROOT_OK=1
fi
if [ "$ROOT_OK" = "1" ]; then
  MYSQL_CMD="mysql -uroot supermarket_system"
  echo "    （使用 root 免密连接）"
elif [ -n "${MYPASS:-}" ]; then
  MYSQL_CMD="mysql -usupermarket -p${MYPASS} supermarket_system"
  echo "    （使用 supermarket 账号 + SUPERMARKET_DB_PASS）"
else
  # 两种都不可用时**中止**，而不是跳过 —— 裂图一旦上线是用户直接能看到的事故
  echo "❌ 无法连接生产库，无法执行图片引用校验。"
  echo "   请确认服务器 root 可免密连 mysql，或 export SUPERMARKET_DB_PASS='<prod密码>' 后重试。"
  echo "   （已中止发布，线上未切换。）"
  exit 1
fi
TMP=$(mktemp)
for pair in "product:cover_url" "banner:image_url" "category:image_url" "flash_sale:image_url" "activity:image_url" "carousel:image_url" "announcement:image_url"; do
  tbl=${pair%%:*}; col=${pair##*:}
  $MYSQL_CMD -N -B -e "SELECT $col FROM $tbl WHERE $col IS NOT NULL AND $col != ''" 2>/dev/null | while IFS= read -r u; do
    if echo "$u" | grep -q '^/seed'; then f="$STAGE_DIR$u";
    elif echo "$u" | grep -q '^/api/uploads'; then f="/opt/supermarket${u#/api}";
    elif echo "$u" | grep -q '^/uploads'; then f="/opt/supermarket$u"; else f=""; fi
    if [ -n "$f" ] && [ ! -f "$f" ]; then echo "MISSING[$tbl]: $u -> $f"; fi
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

# 原子切换：同分区 rename 是瞬时操作，访客零感知；旧目录留作 _bak 便于回滚
ssh "$SERVER_HOST" "rm -rf ${BACKUP_WEB_DIR} && mv ${REMOTE_WEB_DIR} ${BACKUP_WEB_DIR} && mv ${STAGE_DIR} ${REMOTE_WEB_DIR} && chown -R ${RUN_USER}:${RUN_USER} ${REMOTE_WEB_DIR}"
echo "✅ 前端已切换（旧目录保留在 ${BACKUP_WEB_DIR}）。"

echo "==> [5/5] 切换后端 jar 并重启（停机窗口仅 mv + start）"
# 到这里所有耗时/易错的活都做完了，剩下就是"换文件 + 起服务"，通常 3~5 秒。
STOPPED=1
ssh "$SERVER_HOST" "
set -e
cd ${REMOTE_APP_DIR}
[ -f app.jar ] && cp -f app.jar app.jar.bak || true
systemctl stop supermarket
mv -f ${REMOTE_JAR_NEW} ${REMOTE_JAR}
chown ${RUN_USER}:${RUN_USER} ${REMOTE_JAR}
systemctl daemon-reload
systemctl start supermarket
"
sleep 6
ssh "$SERVER_HOST" "systemctl is-active supermarket && systemctl status supermarket --no-pager | head -n 8"
STOPPED=0

echo
echo "==> 发布完成。"
echo "    前端： http://${SERVER_HOST#root@}"
echo "    健康检查： ssh ${SERVER_HOST} 'curl -s -o /dev/null -w \"%{http_code}\" http://127.0.0.1:8080/api/products?size=1'"
echo "    日志： ssh ${SERVER_HOST} 'journalctl -u supermarket -n 100 --no-pager'"
echo "    回滚后端： ssh ${SERVER_HOST} 'cd ${REMOTE_APP_DIR} && cp app.jar.bak app.jar && systemctl restart supermarket'"
echo "    回滚前端： ssh ${SERVER_HOST} 'rm -rf ${REMOTE_WEB_DIR} && mv ${BACKUP_WEB_DIR} ${REMOTE_WEB_DIR}'"
