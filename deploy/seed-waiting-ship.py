"""造一笔「待寄回」(WAITING_SHIP) 的售后单，供小程序/PC 的寄回链路手测。

链路（全走真实接口）：注册 → quick-buy → 支付 → 管理员备货完成(SHIPPED)
→ 申请退货退款(type=RETURN) → 管理员同意且需寄回(needReturn=true)。

用法：
    python seed-waiting-ship.py                # 本地 8080
    BASE=https://haomart.xyz/api python seed-waiting-ship.py

跑完会打印 USERNAME / PASSWORD / ORDER / RETURN。
清理：本地用 SQL 按 user_id 删（注意 SHIPPED 单未被退款，删单要按 order_item 手工回补库存）；
线上用 deploy/clean-test-user.sh <username>（它不回补库存，需同样手工补）。
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _admin_token import temp_admin

BASE = os.environ.get("BASE", "http://localhost:8080/api")
PWD = "Test@12345"
PRODUCT_ID = int(os.environ.get("PRODUCT_ID", "2"))   # 矿泉水 2.50，稳定有货
USER = "vfyship%d" % (int(time.time()) % 100000)


def http(method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        raw = e.read().decode() or "{}"
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"raw": raw[:300]}


def fail(msg):
    print("FAIL " + msg)
    sys.exit(1)


st, reg = http("POST", "/auth/register", None, {
    "username": USER, "password": PWD, "nickname": "寄回探针",
    # 手机号前缀要与 _admin_token.temp_admin 的 "13"+ts 错开，否则同一秒注册撞唯一键
    "phone": "15" + str(int(time.time()))[-9:].zfill(9), "email": USER + "@test.com"})
if reg.get("code") != 0:
    fail("注册失败 HTTP %s %s" % (st, reg))
st, lg = http("POST", "/auth/login", None, {"username": USER, "password": PWD})
tok = (lg.get("data") or {}).get("token")
if not tok:
    fail("登录失败 %s" % lg)

with temp_admin() as adm:
    TA = adm.token
    adm.sql("UPDATE sys_user SET balance=1000 WHERE username='%s'" % USER)

    st, ob = http("POST", "/orders/quick-buy", tok, {
        "productId": PRODUCT_ID, "quantity": 1,
        "fulfillmentType": "PICKUP", "pickupStoreId": 1})
    oid = (ob.get("data") or {}).get("id")
    if not oid:
        fail("下单失败 %s" % ob)
    http("POST", "/orders/%s/pay" % oid, tok)
    st, rd = http("POST", "/admin/orders/%s/ready" % oid, TA)
    print("order=%s status=%s" % (oid, (rd.get("data") or {}).get("status")))

    st, ret = http("POST", "/returns", tok, {
        "orderId": oid, "type": "RETURN", "reason": "商品质量问题"})
    rid = (ret.get("data") or {}).get("id")
    if not rid:
        fail("申请退货失败 HTTP %s %s" % (st, ret))

    st, rev = http("POST", "/admin/return-requests/%s/review" % rid, TA,
                   {"approved": True, "needReturn": True, "remark": "请寄回后联系客服"})
    print("review HTTP %s retStatus=%s" % (st, (rev.get("data") or {}).get("status")))

    row = adm.sql("SELECT CONCAT(status,'|',IFNULL(refund_status,'-')) FROM orders WHERE id=%s" % oid)
    print("RESULT order_row=%s" % str(row).strip())

print("USERNAME=%s PASSWORD=%s ORDER=%s RETURN=%s" % (USER, PWD, oid, rid))
