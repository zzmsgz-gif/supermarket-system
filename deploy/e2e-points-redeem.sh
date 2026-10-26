#!/usr/bin/env bash
# 2026-10-24 积分抵扣口径端到端验证：
# 注册临时号 → 注入 余额24/冻结194（超冻状态）→ usePoints 下单 → 断言积分实际抵扣成功（冻结不再锁用）
set -uo pipefail
BASE="https://haomart.xyz/api"
TS="$(date +%H%M%S)"
USER="e2erdm_$TS"
PASS="Test@12345"
PRODUCT_ID=2   # 矿泉水 2.50 有货
PTS_TO_USE=10  # 用 10 分 = ¥0.10（上限=应付50% → 125分，不会顶到 cap）

jget() { python3 -c "import sys,json;d=json.load(sys.stdin);print(d$1)"; }

echo "== 1) 注册 $USER =="
REG=$(curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\",\"nickname\":\"e2e\",\"phone\":\"13800138000\",\"email\":\"$USER@test.com\"}")
[ "$(echo "$REG" | jget "['code']")" = "0" ] || { echo "REGISTER_FAIL: $(echo "$REG" | head -c 300)"; exit 1; }

echo "== 2) 登录 =="
TOKEN=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" | jget "['data']['token']")
[ -n "$TOKEN" ] || { echo "LOGIN_FAIL"; exit 1; }
AUTH="Authorization: Bearer $TOKEN"

echo "== 3) 注入 余额24/冻结194（模拟超冻状态）=="
ssh -o BatchMode=yes -o ConnectTimeout=15 root@8.218.154.150 'bash -s' -- "$USER" <<'EOF' 2>&1 | grep -v "WARNING\|post-quantum\|openssh.com\|upgraded\|Using a password"
UN="$1"
ENVLINE="$(systemctl show supermarket -p Environment | sed 's/^Environment=//')"
U="$(printf '%s\n' "$ENVLINE" | tr ' ' '\n' | sed -n 's/^DB_USERNAME=//p' | head -1)"
P="$(printf '%s\n' "$ENVLINE" | tr ' ' '\n' | sed -n 's/^DB_PASSWORD=//p' | head -1)"
mysql -u"$U" -p"$P" supermarket_system -e "UPDATE sys_user SET points=24, frozen_points=194 WHERE username='$UN';"
EOF
echo "injected"

echo "== 4) 建地址 =="
ADDR_ID=$(curl -s -X POST "$BASE/addresses" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"receiverName":"e2e","receiverPhone":"13800138000","province":"广东省","city":"深圳市","district":"南山区","detailAddress":"测试地址1号","isDefault":true}' | jget "['data']['id']")
[ -n "$ADDR_ID" ] && [ "$ADDR_ID" != "None" ] || { echo "ADDR_FAIL"; exit 1; }

echo "== 5) usePoints=true 下单（冻结 194 > 余额 24，验证抵扣不再被锁）=="
ORDER=$(curl -s -X POST "$BASE/orders/quick-buy" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"productId\":$PRODUCT_ID,\"quantity\":1,\"addressId\":$ADDR_ID,\"usePoints\":true,\"pointsToUse\":$PTS_TO_USE}")
OID=$(echo "$ORDER" | jget "['data']['id']")
[ -n "$OID" ] && [ "$OID" != "None" ] || { echo "ORDER_FAIL: $(echo "$ORDER" | head -c 400)"; exit 1; }
USED=$(echo "$ORDER" | jget "['data']['pointsUsed']")
DISC=$(echo "$ORDER" | jget "['data']['pointsDiscount']")
echo "orderId=$OID pointsUsed=$USED pointsDiscount=$DISC"

echo "== 6) 断言 =="
if [ "$USED" = "10" ]; then
  echo "===RESULT: PASS_抵扣在冻结状态下成功使用==="
else
  echo "===RESULT: FAIL pointsUsed=$USED (期望10)==="
fi

echo "TEST_USER=$USER ORDER_ID=$OID STOCK_TO_RESTORE=1"
