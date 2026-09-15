"""为「会员积分」前端页面做真机（浏览器）验证准备一个演示账号。

产出：/tmp/member-browser-session.json，含 token + user(JSON) + uid，
供 agent-browser 注入 localStorage(supermarket_token / supermarket_user) 使用。

用完请执行 cleanup-browser-member.py <uid> 清理。
"""
import json
import subprocess
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
DBPASS = "zzmsgz"
STAMP = str(int(time.time()))
u = "bdemo_" + STAMP
PW = "Bdemo123"
phone = "137" + STAMP[-8:]


def call(method, path, body=None, token=None):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Content-Type": "application/json",
                 **({"Authorization": "Bearer " + token} if token else {})},
        method=method)
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode()).get("data")


def sql(stmt):
    p = subprocess.run([MYSQL, "-uroot", "-p" + DBPASS, "-D", "supermarket_system",
                        "--default-character-set=utf8mb4", "-N", "-B", "-e", stmt],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if p.returncode != 0:
        raise RuntimeError("SQL fail: " + p.stderr)
    return p.stdout.strip()


tok = call("POST", "/auth/register", {"username": u, "password": PW,
                                      "nickname": "会员演示", "phone": phone})["token"]
uid = call("GET", "/auth/me", None, tok)["id"]

# 让它成为「银卡会员」：累计消费 1200、积分 520、余额 500
sql(f"UPDATE supermarket_system.sys_user SET points=520, total_spent=1200, "
    f"member_level=1, balance=500 WHERE id={uid}")

# 造几条积分流水，让「积分明细」页有内容
sql("INSERT INTO supermarket_system.point_ledger "
    "(user_id,type,amount,balance_after,ref_order_id,remark,created_at) VALUES "
    f"({uid},'EARN',450,450,NULL,'新人注册赠送',NOW() - INTERVAL 3 DAY),"
    f"({uid},'EARN',120,570,NULL,'消费获得积分',NOW() - INTERVAL 2 DAY),"
    f"({uid},'REDEEM',50,520,NULL,'下单积分抵扣',NOW() - INTERVAL 1 DAY)")

# 购物车 + 收货地址，让结算页可用
prods = call("GET", "/products?page=1&size=20", None, tok)["items"]
picked = [p for p in prods if p["stock"] > 5][:2]
for p in picked:
    call("POST", "/cart/items", {"productId": p["id"], "quantity": 2}, tok)
call("POST", "/addresses", {"receiverName": "李四", "receiverPhone": "13900000000",
                            "province": "广东省", "city": "深圳市", "district": "福田区",
                            "detailAddress": "演示路 8 号", "isDefault": True}, tok)

me = call("GET", "/auth/me", None, tok)
print("UID=%s  USERNAME=%s  PW=%s" % (uid, u, PW))
print("POINTS=%s LEVEL=%s SPENT=%s BALANCE=%s" % (me["points"], me["memberLevel"],
                                                  me["totalSpent"], me["balance"]))
# 输出成单行 JSON，方便上层直接拿去注入 localStorage
print("SESSION_JSON=" + json.dumps({"uid": uid, "username": u, "password": PW,
                                    "token": tok, "user": me}, ensure_ascii=False))
