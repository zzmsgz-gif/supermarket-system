#!/usr/bin/env bash
# #711 备注端到端验证：注册→登录→地址→带备注 quickBuy→回读订单，确认 remark 经 API 返回
set -uo pipefail
BASE="https://haomart.xyz/api"
TS="$(date +%H%M%S)"
USER="e2e711_$TS"
PASS="Test@12345"
REMARK="E2E711_备注验证_$TS"
PRODUCT_ID=2   # 矿泉水 2.50 有货

jget() { python3 -c "import sys,json;d=json.load(sys.stdin);print(d$1)"; }

echo "== 1) 注册 $USER =="
REG=$(curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\",\"nickname\":\"e2e\",\"phone\":\"13800138000\",\"email\":\"$USER@test.com\"}")
echo "$REG" | head -c 200; echo
CODE=$(echo "$REG" | jget "['code']")
[ "$CODE" = "0" ] || { echo "REGISTER_FAIL code=$CODE"; echo "$REG" | head -c 400; exit 1; }

echo "== 2) 登录取 token =="
LOGIN=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}")
TOKEN=$(echo "$LOGIN" | jget "['data']['token']")
[ -n "$TOKEN" ] || { echo "LOGIN_FAIL: $(echo "$LOGIN" | head -c 300)"; exit 1; }
echo "token 长度=${#TOKEN}"
AUTH="Authorization: Bearer $TOKEN"

echo "== 3) 建地址 =="
ADDR=$(curl -s -X POST "$BASE/addresses" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"receiverName":"e2e","receiverPhone":"13800138000","province":"广东省","city":"深圳市","district":"南山区","detailAddress":"测试地址1号","isDefault":true}')
ADDR_ID=$(echo "$ADDR" | jget "['data']['id']")
[ -n "$ADDR_ID" ] && [ "$ADDR_ID" != "None" ] || { echo "ADDR_FAIL: $(echo "$ADDR" | head -c 300)"; exit 1; }
echo "addressId=$ADDR_ID"

echo "== 4) 带备注 quickBuy =="
ORDER=$(curl -s -X POST "$BASE/orders/quick-buy" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"productId\":$PRODUCT_ID,\"quantity\":1,\"addressId\":$ADDR_ID,\"remark\":\"$REMARK\"}")
OID=$(echo "$ORDER" | jget "['data']['id']")
[ -n "$OID" ] && [ "$OID" != "None" ] || { echo "ORDER_FAIL: $(echo "$ORDER" | head -c 400)"; exit 1; }
echo "orderId=$OID"

echo "== 5) 回读订单详情，校验 remark =="
GOT=$(curl -s "$BASE/orders/$OID" -H "$AUTH")
GOT_REMARK=$(echo "$GOT" | jget "['data']['remark']")
echo "回读 remark = [$GOT_REMARK]"
echo "下单时 remark = [$REMARK]"
if [ "$GOT_REMARK" = "$REMARK" ]; then echo "===RESULT: PASS_711_REMARK_RETURNED==="; else echo "===RESULT: FAIL_711==="; fi
echo "TEST_USER=$USER"
