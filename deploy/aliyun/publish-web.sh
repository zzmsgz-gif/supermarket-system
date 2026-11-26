#!/usr/bin/env bash
# 发布 PC 站静态产物（frontend/dist）到 https://haomart.xyz/
#
# 为什么不直接 rsync/覆盖 dist：
#   1) 现网 assets/ 里累积了历史版本的 chunk（984 个文件 vs dist 的 211 个）。
#      直接替换会把还停留在旧页面（懒加载没触发）的用户打到 404。
#      所以先在服务器上把现网整目录复制成 stage，再把新产物解压覆盖上去。
#   2) mv 是同一分区内的 rename，两次 mv 之间只有微秒级空窗，比原地删了再传安全得多。
#   3) 必须保留 /var/www/supermarket/m（小程序 H5 版）—— cp -a 整目录天然带过来。
#
# 用法：bash deploy/aliyun/publish-web.sh           # 构建 + 发布
#       SKIP_BUILD=1 bash deploy/aliyun/publish-web.sh   # 用现有 dist 直接发布
set -euo pipefail

SERVER="${SERVER:-root@8.218.154.150}"
WEBROOT="${WEBROOT:-/var/www/supermarket}"
DIST="frontend/dist"
SKIP_BUILD="${SKIP_BUILD:-0}"
TS="$(date +%Y%m%d%H%M%S)"

cd "$(dirname "$0")/../.."

if [ "$SKIP_BUILD" != "1" ]; then
  echo "==> 构建 PC 前端"
  # 注意：dev server（5173）跑着会锁住 dist 里的文件，构建前要先停掉
  ( cd frontend && NODE_OPTIONS= npx vite build )
fi

[ -f "$DIST/index.html" ] || { echo "!! dist 缺 index.html"; exit 1; }
COUNT=$(find "$DIST" -type f | wc -l)
echo "==> 新产物 $COUNT 个文件，$(du -sh "$DIST" | cut -f1)"

TARBALL="web-$TS.tar.gz"
tar -czf "/tmp/$TARBALL" -C "$DIST" .
scp -q "/tmp/$TARBALL" "$SERVER:/tmp/$TARBALL"

echo "==> 服务器：复制现网为 stage → 覆盖新产物 → 校验"
ssh "$SERVER" "set -e
  STAGE=$WEBROOT.stage-$TS
  rm -rf \$STAGE
  cp -a $WEBROOT \$STAGE
  tar -xzf /tmp/$TARBALL -C \$STAGE

  # --- 完整性校验：任何一项不过就放弃，现网一个字节都不动 ---
  [ -s \$STAGE/index.html ] || { echo '!! stage/index.html 缺失或为空'; exit 1; }
  echo \"    index.html \$(wc -c < \$STAGE/index.html) bytes\"

  # index.html 里引用的每个 assets 必须真实存在（防「入口指向不存在的 chunk」白屏）
  MISS=0
  for f in \$(grep -o '/assets/[A-Za-z0-9._-]*' \$STAGE/index.html | sort -u); do
    [ -f \"\$STAGE\$f\" ] || { echo \"!! 入口引用的 \$f 不存在\"; MISS=1; }
  done
  [ \$MISS -eq 0 ] || exit 1

  # 图片目录不能比现网少（本次没改图，只防止误删）
  for d in seed-products seed-avatars; do
    OLD=\$(find $WEBROOT/\$d -type f 2>/dev/null | wc -l)
    NEW=\$(find \$STAGE/\$d -type f 2>/dev/null | wc -l)
    echo \"    \$d: 现网 \$OLD / stage \$NEW\"
    [ \"\$NEW\" -ge \"\$OLD\" ] || { echo \"!! \$d 文件变少，放弃切换\"; exit 1; }
  done

  # 小程序 H5 版不能丢
  [ -f \$STAGE/m/index.html ] || { echo '!! stage 里没有 m/index.html（H5 版会失效）'; exit 1; }

  # --- 原子切换 ---
  rm -rf $WEBROOT.bak-$TS
  mv $WEBROOT $WEBROOT.bak-$TS
  mv \$STAGE $WEBROOT
  chown -R root:root $WEBROOT
  rm -f /tmp/$TARBALL
  echo '    switched'
"
rm -f "/tmp/$TARBALL"

echo "==> 线上校验"
http_code() { curl -s -o /dev/null -w "%{http_code}" "$1" 2>/dev/null || true; }
for u in https://haomart.xyz/ https://haomart.xyz/shop https://haomart.xyz/m/; do
  C=$(http_code "$u"); [ -n "$C" ] || C=000
  echo "    $u -> $C"
  [ "$C" = "200" ] || { echo "!! 非 200，回滚：服务器上 mv $WEBROOT.bak-$TS $WEBROOT"; exit 1; }
done
NEWCHUNK=$(curl -s https://haomart.xyz/ 2>/dev/null | grep -o '/assets/index-[A-Za-z0-9_-]*\.js' | head -1 || true)
if [ -n "${NEWCHUNK:-}" ]; then
  C=$(http_code "https://haomart.xyz$NEWCHUNK"); [ -n "$C" ] || C=000
  echo "    $NEWCHUNK -> $C"
fi
echo "==> 完成。回滚点：$WEBROOT.bak-$TS"
