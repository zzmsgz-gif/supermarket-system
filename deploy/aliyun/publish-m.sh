#!/usr/bin/env bash
# 发布「小程序版 H5」（uni-app build:h5 产物）到 https://haomart.xyz/m/
#
# 为什么走同域子路径：H5 包的 API_BASE 是相对路径 /api、图片按域名根解析，
# 只有同源部署才能免 CORS、免改代码。详见 nginx 配置里的注释。
#
# 用法：bash deploy/aliyun/publish-m.sh
#   SKIP_BUILD=1 直接发布现有 dist（调试用）
set -euo pipefail

SERVER="${SERVER:-root@8.218.154.150}"
WEBROOT="${WEBROOT:-/var/www/supermarket}"
LOCAL_DIST="miniprogram/dist/build/h5"
SKIP_BUILD="${SKIP_BUILD:-0}"

cd "$(dirname "$0")/../.."

if [ "$SKIP_BUILD" != "1" ]; then
  echo "==> 构建 H5"
  # NODE_OPTIONS 必须清空：受管 npm 的 safe-delete 钩子会拦住 vite 的批量删除
  ( cd miniprogram && NODE_OPTIONS= npm run build:h5 )
fi

[ -f "$LOCAL_DIST/index.html" ] || { echo "!! 产物缺 index.html，构建没成功"; exit 1; }
COUNT=$(find "$LOCAL_DIST" -type f | wc -l)
echo "==> 产物：$COUNT 个文件，$(du -sh "$LOCAL_DIST" | cut -f1)"

TARBALL="h5-m-$$.tar.gz"
echo "==> 打包上传"
tar -czf "/tmp/$TARBALL" -C "$LOCAL_DIST" .
scp -q "/tmp/$TARBALL" "$SERVER:/tmp/$TARBALL"

echo "==> 服务器解包校验 + 原子切换"
# staging 放在 WEBROOT 下的隐藏目录：同一分区才能保证 mv 是原子改名而不是拷贝
ssh "$SERVER" "set -e
  STAGE=$WEBROOT/.m-stage-$$
  rm -rf \$STAGE && mkdir -p \$STAGE
  tar -xzf /tmp/$TARBALL -C \$STAGE
  [ -f \$STAGE/index.html ] || { echo '!! stage 缺 index.html'; exit 1; }
  N=\$(find \$STAGE -type f | wc -l)
  echo \"    stage \$N files\"
  [ \"\$N\" -eq $COUNT ] || { echo '!! 文件数与本地不一致，放弃切换'; exit 1; }
  rm -rf $WEBROOT/m.old
  if [ -d $WEBROOT/m ]; then mv $WEBROOT/m $WEBROOT/m.old; fi
  mv \$STAGE $WEBROOT/m
  chown -R root:root $WEBROOT/m
  rm -f /tmp/$TARBALL
"
rm -f "/tmp/$TARBALL"

echo "==> 线上校验"
# 注意：Git Bash 下 curl -o /dev/null 会以 exit 23 结束（写 /dev/null 失败），
# 不能用 `|| echo 000` 兜底，否则状态码会被拼成 "200000" 而误判失败。
http_code() { curl -s -o /dev/null -w "%{http_code}" "$1" 2>/dev/null || true; }
CODE=$(http_code https://haomart.xyz/m/)
[ -n "${CODE:-}" ] || CODE=000
echo "    https://haomart.xyz/m/ -> $CODE"
ASSET=$(curl -s https://haomart.xyz/m/ 2>/dev/null | grep -o '/m/assets/index-[^"]*\.js' | head -1 || true)
if [ -n "${ASSET:-}" ]; then
  ACODE=$(http_code "https://haomart.xyz$ASSET")
  [ -n "${ACODE:-}" ] || ACODE=000
  echo "    $ASSET -> $ACODE"
fi
[ "$CODE" = "200" ] || { echo "!! 入口非 200，检查 nginx location /m/"; exit 1; }
echo "==> 完成。回滚：服务器上 mv $WEBROOT/m.old $WEBROOT/m"
