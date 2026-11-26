"""端到端验证：立即购买(quick-buy)建单不再 500，且 order_item.refunded 以 0 落库。

背景：2026-10-16 生产故障 —— OrderItem.refunded 实体字段为 null，
Hibernate 显式往 INSERT 写 NULL，撞 NOT NULL → 所有建单 500；
前端立即购买分支又没 catch，用户看到「点提交没反应」。

本脚本：注册临时探针号 → 登录 → 建地址 → 立即购买下单 → 校验 refunded=0 → 清理探针数据。
通过 SSH stdin 在服务器上跑（stdin 传脚本，只有一条连接，抗网络抖动）。
"""
import json
import subprocess
import urllib.error
import urllib.request

BASE = "http://127.0.0.1/api"
USER = "probe_order_tmp"
PWD = "Test123456"
MYSQL = ["mysql", "-usupermarket", "-phjkoW4LhISJfmJNOVK0t1Yhx", "supermarket_system", "-e"]


def req(method, path, body=None, tok=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if tok:
        headers["Authorization"] = "Bearer " + tok
    r = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            return json.loads(resp.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return json.loads(raw or "{}")
        except Exception:
            return {"code": e.code, "message": raw[:200]}


def sql(q):
    out = subprocess.run(MYSQL + [q], capture_output=True, text=True)
    return out.stdout.strip()


print("1) register:", req("POST", "/auth/register", {"username": USER, "password": PWD, "nickname": "下单探针"}).get("message"))
lg = req("POST", "/auth/login", {"username": USER, "password": PWD})
tok = (lg.get("data") or {}).get("token") or lg.get("token")
print("2) login token len:", len(tok or ""))
if not tok:
    print("LOGIN FAILED:", lg)
    raise SystemExit(1)

addr = req("POST", "/addresses", {
    "receiverName": "探针", "receiverPhone": "13900000000",
    "province": "北京市", "city": "北京市", "district": "朝阳区",
    "detailAddress": "探针地址", "isDefault": True,
}, tok)
addr_id = (addr.get("data") or {}).get("id") or addr.get("id")
print("3) address id:", addr_id)

prod = req("GET", "/products?size=5")
items = (prod.get("data") or {}).get("content") or []
pick = next((p for p in items if p.get("status") == "ON_SALE" and (p.get("stock") or 0) > 0), None)
print("4) product:", pick and pick.get("name"), "id=", pick and pick.get("id"))
if not pick or not addr_id:
    print("PREREQ FAILED", addr, prod)
    raise SystemExit(1)

res = req("POST", "/orders/quick-buy", {
    "productId": pick["id"], "quantity": 1, "skuSpec": None, "remark": "probe",
    "fulfillmentType": "EXPRESS", "addressId": addr_id,
}, tok)
data = res.get("data") or {}
oid = data.get("id")
print("5) quick-buy code:", res.get("code"), "orderId:", oid, "msg:", res.get("message"))
if not oid:
    print("ORDER CREATE FAILED:", res)
    raise SystemExit(1)

print("6) order_item refunded rows:")
print(sql("SELECT id, refunded, refunded IS NULL AS is_null FROM order_item WHERE order_id=%d;" % oid))

# ---- 清理探针数据：只删 probe 自己建的，绝不碰别人 ----
sql("DELETE FROM order_item WHERE order_id=%d;" % oid)
sql("DELETE FROM orders WHERE id=%d;" % oid)
sql("DELETE FROM user_address WHERE user_id=(SELECT id FROM sys_user WHERE username='%s');" % USER)
sql("DELETE FROM sys_user WHERE username='%s';" % USER)
print("7) cleanup done; probe user left:", sql("SELECT COUNT(*) FROM sys_user WHERE username='%s';" % USER))
