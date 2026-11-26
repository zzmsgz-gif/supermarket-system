"""端到端验证（本机 → 公网 HTTPS）：立即购买 quick-buy 建单不再 500。
目标：确认 2026-10-16 的 refunded NOT NULL 故障已修复，且不会再用 NULL 撞库。
说明：本机无 mysql 权限，只验证「建单返回 200 + 拿到订单号」这关键一步；
DB 层 refunded=0 已由 javap 字节码核验（构造函数 putfield refunded = Boolean.valueOf(false)）。
"""
import json
import random
import sys
import time
import urllib.error
import urllib.request

BASE = "https://haomart.xyz/api"
STAMP = time.strftime("%m%d%H%M%S")
USER = "pqb" + STAMP
PHONE = "13" + "".join(random.choice("0123456789") for _ in range(9))
PWD = "Test123456"


def req(method, path, body=None, tok=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if tok:
        headers["Authorization"] = "Bearer " + tok
    r = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            return json.loads(resp.read().decode() or "{}"), resp.status
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return json.loads(raw or "{}"), e.code
        except Exception:
            return {"code": e.code, "message": raw[:200]}, e.code


print("== 1) register", USER)
reg, code = req("POST", "/auth/register", {"username": USER, "password": PWD, "nickname": "下单探针", "phone": PHONE})
print("   code:", code, "msg:", reg.get("message"))

lg, code = req("POST", "/auth/login", {"username": USER, "password": PWD})
tok = (lg.get("data") or {}).get("token") or lg.get("token")
print("== 2) login code:", code, "token_len:", len(tok or ""))
if not tok:
    print("LOGIN FAILED:", lg)
    sys.exit(1)

addr, code = req("POST", "/addresses", {
    "receiverName": "探针", "receiverPhone": "13900000000",
    "province": "北京市", "city": "北京市", "district": "朝阳区",
    "detailAddress": "探针地址", "isDefault": True,
}, tok)
addr_id = (addr.get("data") or {}).get("id") or addr.get("id")
print("== 3) address code:", code, "id:", addr_id)

prod, code = req("GET", "/products?size=10")
items = (prod.get("data") or {}).get("items") or (prod.get("data") or {}).get("content") or []
pick = next((p for p in items if p.get("status") == "ON_SALE" and (p.get("stock") or 0) > 0), None)
print("== 4) product code:", code, "picked:", (pick or {}).get("name"), "id=", (pick or {}).get("id"))
if not pick or not addr_id:
    print("PREREQ FAILED", addr, prod)
    sys.exit(1)

res, code = req("POST", "/orders/quick-buy", {
    "productId": pick["id"], "quantity": 1, "skuSpec": None, "remark": "probe",
    "fulfillmentType": "EXPRESS", "addressId": addr_id,
}, tok)
data = res.get("data") or {}
oid = data.get("id")
print("== 5) quick-buy HTTP:", code, "resp_code:", res.get("code"),
      "orderId:", oid, "msg:", res.get("message"))
if oid:
    print("RESULT: PASS —— 立即购买建单成功，refunded NOT NULL 故障已修复")
else:
    print("RESULT: FAIL —— 建单未成功，见上")
    sys.exit(2)

# 取消该订单以免占库存（关单会回补名额）
try:
    cz, _ = req("POST", "/orders/%s/cancel" % oid, {}, tok)
    print("== 6) cancel order:", cz.get("code"), cz.get("message"))
except Exception as e:
    print("== 6) cancel skipped:", e)
print("== probe user left on server:", USER, "(可后续由 SSH 清理)")
