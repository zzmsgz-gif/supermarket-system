#!/usr/bin/env bash
#
# 只发布**后端 jar**（不含前端）。改了 Java 代码后跑这个。
#
# 流程刻意做成"一条命令内闭环"，因为中间任何一步中断都会留下半完成态：
#   备份旧 jar → 停服 → 上传 → 校验（sha256 + zip 无损）→ 启动（失败则回滚旧 jar 后启动）
#
# 两条硬约束（都来自真实事故）：
# 1. **绝不能在 jar 运行中直接 scp 覆盖** —— 运行中的 JVM 持有已加载的类，
#    覆盖后触发 NoClassDefFound，整站 /api 全挂。
# 2. **上传后必须校验** —— 2026-10-28 有一次 scp 把 70MB 的 jar 传坏（zip 在某个 .class 处断裂），
#    脚本没校验就 restart，导致生产崩溃重启循环。所以 sha256 与 zip 必须双过才启动。
#
# 用法： bash deploy/aliyun/release-backend.sh
# 可用环境变量覆盖：SM_HOST / SM_APP_DIR
set -uo pipefail

HOST="${SM_HOST:-root@8.218.154.150}"
APP_DIR="${SM_APP_DIR:-/opt/supermarket}"
SSH_OPTS=(-o BatchMode=yes -o ConnectTimeout=25)

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
JAR=$(ls "$ROOT"/backend/target/*.jar 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
  echo "❌ backend/target 下没有 jar，先执行："
  echo '   cd backend && JAVA_HOME="E:/java/develope" "D:/develop/apache-maven-3.9.9/bin/mvn.cmd" -q clean package -DskipTests'
  exit 1
fi

LOCAL_SHA=$(sha256sum "$JAR" | awk '{print $1}')
echo "jar  : $(basename "$JAR")  ($(stat -c %s "$JAR") B)"
echo "sha  : $LOCAL_SHA"
echo

echo "--- [1/5] 备份线上旧 jar ---"
ssh "${SSH_OPTS[@]}" "$HOST" "cp -f $APP_DIR/app.jar $APP_DIR/app.jar.bak" && echo "  ✓ app.jar.bak"

echo "--- [2/5] 停止服务（此刻起站点不可用）---"
ssh "${SSH_OPTS[@]}" "$HOST" "systemctl stop supermarket" && echo "  ✓ stopped"

echo "--- [3/5] 上传 jar ---"
scp "${SSH_OPTS[@]}" "$JAR" "$HOST:$APP_DIR/app.jar" >/dev/null && echo "  ✓ uploaded"

echo "--- [4/5] 校验 sha256 + zip 完整性 ---"
REMOTE_SHA=$(ssh "${SSH_OPTS[@]}" "$HOST" "sha256sum $APP_DIR/app.jar" 2>/dev/null | awk '{print $1}' | tr -d '\r')
REMOTE_ZIP=$(ssh "${SSH_OPTS[@]}" "$HOST" "python3 -c \"import zipfile;print('ZIPOK' if zipfile.ZipFile('$APP_DIR/app.jar').testzip() is None else 'ZIPBAD')\"" 2>/dev/null | tr -d '\r')
echo "  remote sha: $REMOTE_SHA"
echo "  zip check : $REMOTE_ZIP"

echo "--- [5/5] 启动 ---"
if [ "$REMOTE_SHA" = "$LOCAL_SHA" ] && [ "$REMOTE_ZIP" = "ZIPOK" ]; then
  ssh "${SSH_OPTS[@]}" "$HOST" "systemctl start supermarket"
  echo "  ✓ 校验通过，已启动"
else
  # 校验不通过也要把服务拉起来 —— 宁可跑旧版本，也不能让站点停在 502
  ssh "${SSH_OPTS[@]}" "$HOST" "cp -f $APP_DIR/app.jar.bak $APP_DIR/app.jar && systemctl start supermarket"
  echo "  ❌ 校验不一致（remote=${REMOTE_SHA:0:12} local=${LOCAL_SHA:0:12} zip=$REMOTE_ZIP）"
  echo "  ⚠️ 已回滚旧 jar 并启动，本次发布失败"
  exit 1
fi

sleep 6
STATUS=$(ssh "${SSH_OPTS[@]}" "$HOST" "systemctl is-active supermarket" 2>/dev/null | tr -d '\r')
echo "服务状态: $STATUS"
if [ "$STATUS" != "active" ]; then
  echo "❌ 服务未 active，查日志： ssh $HOST 'journalctl -u supermarket -n 60 --no-pager'"
  exit 1
fi
echo "✅ 发布完成"
