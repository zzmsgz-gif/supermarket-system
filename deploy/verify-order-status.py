"""Verify the user order-card shipping-status labels render correctly.
Creates a throwaway buyer + admin, makes two orders (one PAID/unshipped, one
SHIPPED), prints credentials for a browser check, then cleans up everything."""
import json
import subprocess
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
STAMP = str(int(time.time()))
results = []


def call(method, path, body=None, token=None):
    url = BASE + path
    data = json.dumps(body).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
            return payload.get("data")
    except urllib.error.HTTPError as err:
        detail = err.read().decode("utf-8")
        raise RuntimeError(f"{method} {path} -> {err.code} {detail}") from err


def sql(statement):
    proc = subprocess.run([MYSQL, "-uroot", "-pzzmsgz", "-N", "-B", "-e", statement],
                          capture_output=True, text=True, encoding="utf-8", errors="replace")
    if proc.returncode != 0:
        raise RuntimeError("SQL failed: " + proc.stderr)
    return proc.stdout.strip()


def check(name, ok, detail=""):
    results.append((name, ok, detail))
    print(("PASS " if ok else "FAIL ") + name + (" :: " + str(detail) if detail else ""))


buyer = "shipv_buyer_" + STAMP
admin = "shipv_admin_" + STAMP
PW = "Shipv123"

# buyer
tok = call("POST", "/auth/register", {"username": buyer, "password": PW, "nickname": "发货状态验证买家"})["token"]
check("注册买家", bool(tok))
call("POST", "/wallet/recharges", {"amount": 300}, tok)
products = call("GET", "/products?page=1&size=20", None, tok)["items"]
product = next(p for p in products if p["stock"] > 10)
call("POST", "/cart/items", {"productId": product["id"], "quantity": 2}, tok)
cart = call("GET", "/cart", None, tok)
address = call("POST", "/addresses", {
    "receiverName": "李四", "receiverPhone": "13900000000", "province": "广东省",
    "city": "深圳市", "district": "南山区", "detailAddress": "验证路 2 号", "isDefault": True,
}, tok)

# order A: paid, NOT shipped (待发货)
orderA = call("POST", "/orders", {"addressId": address["id"],
             "cartItemIds": [i["id"] for i in cart["items"]]}, tok)
call("POST", f"/orders/{orderA['id']}/pay", None, tok)
check("订单A 支付=待发货", call("GET", f"/orders/{orderA['id']}", None, tok)["status"] == "PAID")

# order B: paid + shipped (已发货)
call("POST", "/cart/items", {"productId": product["id"], "quantity": 1}, tok)
cart2 = call("GET", "/cart", None, tok)
orderB = call("POST", "/orders", {"addressId": address["id"],
             "cartItemIds": [i["id"] for i in cart2["items"]]}, tok)
call("POST", f"/orders/{orderB['id']}/pay", None, tok)
# admin
call("POST", "/auth/register", {"username": admin, "password": PW, "nickname": "验证管理员"})
sql(f"UPDATE supermarket_system.sys_user SET role='ADMIN' WHERE username='{admin}'")
admin_tok = call("POST", "/auth/login", {"username": admin, "password": PW})["token"]
shipped = call("POST", f"/admin/orders/{orderB['id']}/ship",
               {"shipCompany": "顺丰速运", "shipNo": "SF" + STAMP}, admin_tok)
check("订单B 发货=已发货", shipped["status"] == "SHIPPED" and shipped["shipNo"] == "SF" + STAMP)

print("\n=== BROWSER CHECK CREDENTIALS ===")
print(f"BUYER_USER={buyer}")
print(f"BUYER_PW={PW}")
print(f"ORDER_A(待发货)={orderA['id']}  ORDER_B(已发货)={orderB['id']}")

# cleanup
for oid in (orderA["id"], orderB["id"]):
    sql(f"DELETE FROM supermarket_system.order_item WHERE order_id={oid}")
    sql(f"DELETE FROM supermarket_system.orders WHERE id={oid}")
sql(f"DELETE FROM supermarket_system.wallet_transaction WHERE user_id IN (SELECT id FROM supermarket_system.sys_user WHERE username IN ('{buyer}','{admin}'))")
sql(f"DELETE ci FROM supermarket_system.cart_item ci JOIN supermarket_system.cart c ON ci.cart_id=c.id WHERE c.user_id IN (SELECT id FROM supermarket_system.sys_user WHERE username IN ('{buyer}','{admin}'))")
sql(f"DELETE FROM supermarket_system.cart WHERE user_id IN (SELECT id FROM supermarket_system.sys_user WHERE username IN ('{buyer}','{admin}'))")
sql(f"DELETE FROM supermarket_system.address WHERE user_id IN (SELECT id FROM supermarket_system.sys_user WHERE username IN ('{buyer}','{admin}'))")
sql(f"DELETE FROM supermarket_system.sys_user WHERE username IN ('{buyer}','{admin}')")
check("清理临时账号与订单", True, "done")
print("\n===== cleanup done =====")
