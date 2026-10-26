"""造一条「订单列表卡片提交寄回」复现数据：PAID 订单 + WAITING_SHIP 售后单。

步骤：注册买家 → 建地址 → quick-buy → WECHAT 支付 → 申请退款 → 管理员 needReturn=true 通过。
输出登录态给浏览器测试用（stdout JSON）。
"""
import json
import random
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"
TS = time.strftime("%m%d%H%M%S")
U = f"shipcard_{TS}"
ADM = f"shipadm_{TS}"
PWD = "Test123456"


def api(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode() or "{}")
        except Exception:
            return {"code": e.code, "message": e.reason}


def reg_login(username):
    phone = "13" + "".join(random.choice("0123456789") for _ in range(9))
    api("POST", "/auth/register", {"username": username, "password": PWD,
                                   "nickname": "vf", "phone": phone})
    la = api("POST", "/auth/login", {"username": username, "password": PWD})
    d = la.get("data") or {}
    return d.get("token"), (d.get("user") or {}).get("id")


adm_tok, adm_uid = reg_login(ADM)
import subprocess
subprocess.run(["C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql", "-uroot", "-pzzmsgz",
                "-N", "-B", "supermarket_system", "--default-character-set=utf8mb4",
                "-e", f"UPDATE sys_user SET role='ADMIN' WHERE id={adm_uid}"],
               capture_output=True, text=True)
adm_tok = (api("POST", "/auth/login", {"username": ADM, "password": PWD}).get("data") or {}).get("token")
assert adm_tok, "admin token missing"

tok, uid = reg_login(U)
a = api("POST", "/addresses", {"receiverName": "验", "receiverPhone": "13800000000",
                               "province": "北京市", "city": "北京市", "district": "朝阳区",
                               "detailAddress": "复现地址", "isDefault": True}, tok)
addr = (a.get("data") or {}).get("id")
prods = api("GET", "/products?size=100").get("data", {}).get("items", [])
p = next(x for x in prods if x.get("status") == "ON_SALE" and (x.get("stock") or 0) > 3)
o = api("POST", "/orders/quick-buy",
        {"productId": p["id"], "quantity": 1, "skuSpec": None, "remark": "复现",
         "fulfillmentType": "EXPRESS", "addressId": addr}, tok)
oid = (o.get("data") or {}).get("id")
api("POST", f"/orders/{oid}/pay?channel=WECHAT", {}, tok)
app = api("POST", "/returns", {"orderId": int(oid), "type": "REFUND", "reason": "不想要了"}, tok)
rid = (app.get("data") or {}).get("id")
rev = api("POST", f"/admin/return-requests/{rid}/review",
          {"approved": True, "needReturn": True, "remark": "需寄回"}, adm_tok)
status = (rev.get("data") or {}).get("status")
print(json.dumps({"username": U, "password": PWD, "orderId": oid, "returnId": rid,
                  "returnStatus": status, "product": p.get("name")}, ensure_ascii=False))
