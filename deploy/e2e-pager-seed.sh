#!/usr/bin/env bash
# 临时号 + 注入大量流水，强制金额明细分页 >1 页，用于验证分页组件渲染（无报错）。
set -uo pipefail
BASE="https://haomart.xyz/api"
TS="$(date +%H%M%S)"
USER="e2epager_$TS"
PASS="Test@12345"
PHONE="139${TS}$(printf '%02d' $((RANDOM%100)))"
jget() { python3 -c "import sys,json;d=json.load(sys.stdin);print(d$1)"; }

echo "== 1) 注册 $USER =="
REG=$(curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\",\"nickname\":\"e2epager\",\"phone\":\"$PHONE\",\"email\":\"$USER@test.com\"}")
CODE=$(echo "$REG" | jget "['code']")
[ "$CODE" = "0" ] || { echo "REGISTER_FAIL code=$CODE"; echo "$REG" | head -c 400; exit 1; }

echo "== 2) 登录取 token + userId =="
LOGIN=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}")
TOKEN=$(echo "$LOGIN" | jget "['data']['token']")
MYUID=$(echo "$LOGIN" | jget "['data']['user']['id']")
[ -n "$TOKEN" ] || { echo "LOGIN_FAIL: $(echo "$LOGIN" | head -c 300)"; exit 1; }
echo "USER=$USER PASS=$PASS TOKEN=$TOKEN UID=$MYUID"
echo "$LOGIN" | python3 -c "import sys,json;d=json.load(sys.stdin);print(json.dumps(d['data']['user']))" > /tmp/e2epager_user.json

echo "== 3) 注入 50 条流水(强制 3 页) =="
ssh -o BatchMode=yes -o ConnectTimeout=15 root@8.218.154.150 'bash -s' <<EOF 2>&1 | grep -v "WARNING\|post-quantum\|openssh.com\|upgraded\|Using a password"
ENVLINE="\$(systemctl show supermarket -p Environment | sed 's/^Environment=//')"
U="\$(printf '%s\n' "\$ENVLINE" | tr ' ' '\n' | sed -n 's/^DB_USERNAME=//p' | head -1)"
P="\$(printf '%s\n' "\$ENVLINE" | tr ' ' '\n' | sed -n 's/^DB_PASSWORD=//p' | head -1)"
TUID="$MYUID"
VALS=""
for i in \$(seq 1 50); do
  TN="E2EPG_\${TUID}_\${i}"
  VALS="\$VALS('\$TN',\$TUID,'PAYMENT',1.00,0.00,0.00,'SUCCESS',NOW()),"
done
VALS="\${VALS%,}"
mysql -u"\$U" -p"\$P" supermarket_system -e "INSERT INTO wallet_transaction (transaction_no,user_id,type,amount,balance_before,balance_after,status,created_at) VALUES \$VALS;" 2>&1
mysql -u"\$U" -p"\$P" supermarket_system -N -e "SELECT COUNT(*) FROM wallet_transaction WHERE user_id=\$TUID;"
EOF
echo "SEED_DONE UID=$MYUID USER=$USER PASS=$PASS"
