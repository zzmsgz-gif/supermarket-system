#!/usr/bin/env bash
# E2E 验证:会员 profile 积分口径(2026-10-24 起可用=当前余额,冻结只提示不锁抵扣)
# 用法: bash deploy/e2e-profile-points.sh   (本机运行,直连 https://haomart.xyz + SSH 查库)
set -e
BASE="https://haomart.xyz/api"
STAMP="$(date +%H%M%S)"
USERNAME="e2epts_${STAMP}"
PASS="Test123456"

echo "== 1) 注册临时号 $USERNAME =="
curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASS\",\"nickname\":\"E2E积分口径\",\"phone\":\"13800$STAMP\",\"email\":\"$USERNAME@test.com\"}" | head -c 200; echo

echo "== 2) 登录取 token =="
TOKEN="$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASS\"}" | python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")"
[ -n "$TOKEN" ] && echo "TOKEN_OK" || { echo "NO_TOKEN"; exit 1; }

echo "== 3) SSH 给该号注入 余额24/冻结194 (模拟真实超冻状态) =="
ssh -o BatchMode=yes -o ConnectTimeout=15 root@8.218.154.150 'bash -s' -- "$USERNAME" <<'EOF' 2>&1 | grep -v "WARNING\|post-quantum\|openssh.com\|upgraded\|Using a password"
UN="$1"
ENVLINE="$(systemctl show supermarket -p Environment | sed 's/^Environment=//')"
U="$(printf '%s\n' "$ENVLINE" | tr ' ' '\n' | sed -n 's/^DB_USERNAME=//p' | head -1)"
P="$(printf '%s\n' "$ENVLINE" | tr ' ' '\n' | sed -n 's/^DB_PASSWORD=//p' | head -1)"
mysql -u"$U" -p"$P" supermarket_system -e "UPDATE sys_user SET points=24, frozen_points=194 WHERE username='$UN'; SELECT id,username,points,frozen_points FROM sys_user WHERE username='$UN';"
EOF

echo "== 4) GET /member/profile 验证口径 =="
curl -s "$BASE/member/profile" -H "Authorization: Bearer $TOKEN" | python -c "
import sys,json
d=json.load(sys.stdin)['data']
pts,fzn,avl=d['points'],d['frozenPoints'],d['availablePoints']
print(f'points(余额)={pts} frozenPoints={fzn} availablePoints={avl}')
assert pts==24,      f'余额应为24,实际{pts}'
assert fzn==194,     f'冻结应如实=194(不得夹断),实际{fzn}'
assert avl==24,      f'可用应=余额24(冻结不再锁抵扣),实际{avl}'
print('PROFILE_E2E_PASS')
"

echo "== 5) 自清测试号 =="
ssh -o BatchMode=yes -o ConnectTimeout=15 root@8.218.154.150 'bash -s' -- "$USERNAME" <<'EOF' 2>&1 | grep -v "WARNING\|post-quantum\|openssh.com\|upgraded\|Using a password"
UN="$1"
ENVLINE="$(systemctl show supermarket -p Environment | sed 's/^Environment=//')"
U="$(printf '%s\n' "$ENVLINE" | tr ' ' '\n' | sed -n 's/^DB_USERNAME=//p' | head -1)"
P="$(printf '%s\n' "$ENVLINE" | tr ' ' '\n' | sed -n 's/^DB_PASSWORD=//p' | head -1)"
TUID="$(mysql -u"$U" -p"$P" supermarket_system -N -e "SELECT id FROM sys_user WHERE username='$UN';")"
mysql -u"$U" -p"$P" supermarket_system -e "DELETE FROM user_coupon WHERE user_id=$TUID; DELETE FROM user_address WHERE user_id=$TUID; DELETE FROM cart_item WHERE user_id=$TUID; DELETE FROM sys_user WHERE id=$TUID; SELECT COUNT(*) AS remaining FROM sys_user WHERE username='$UN';"
EOF
echo "CLEANUP_DONE"
