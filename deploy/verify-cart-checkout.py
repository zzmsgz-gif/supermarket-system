"""Set up a throwaway buyer with a cart (~50 yuan), 3 received coupons, an
address and enough balance, so the cart/checkout UI can be browser-verified.
Prints credentials, then cleans everything up."""
import json
import subprocess
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
STAMP = str(int(time.time()))


def call(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Content-Type": "application/json", **({"Authorization": "Bearer " + token} if token else {})},
        method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode()).get("data")
    except urllib.error.HTTPError as err:
        raise RuntimeError(f"{method} {path} -> {err.code} {err.read().decode()}")


def sql(stmt):
    p = subprocess.run([MYSQL, "-uroot", "-pzzmsgz", "-D", "supermarket_system",
                        "--default-character-set=utf8mb4", "-N", "-B", "-e", stmt],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if p.returncode != 0:
        raise RuntimeError("SQL fail: " + p.stderr)
    return p.stdout.strip()


buyer = "cartv_" + STAMP
PW = "Cartv123"
tok = call("POST", "/auth/register", {"username": buyer, "password": PW, "nickname": "购物车验证"})["token"]
call("POST", "/wallet/recharges", {"amount": 200}, tok)
for cid in (1, 2, 3):
    call("POST", f"/coupons/{cid}/receive", None, tok)
prods = call("GET", "/products?page=1&size=20", None, tok)["items"]
prod = next(p for p in prods if p["stock"] > 10)
qty = max(1, round(50 / float(prod["price"])))
call("POST", "/cart/items", {"productId": prod["id"], "quantity": qty}, tok)
call("POST", "/addresses", {"receiverName": "王五", "receiverPhone": "13700000000",
    "province": "广东省", "city": "深圳市", "district": "福田区", "detailAddress": "验证路 9 号", "isDefault": True}, tok)
cart = call("GET", "/cart", None, tok)
print("BUYER_USER=%s" % buyer)
print("BUYER_PW=%s" % PW)
print("CART_TOTAL=%.2f  qty=%d  price=%.2f" % (float(cart["selectedAmount"]), qty, float(prod["price"])))
print("SETUP_DONE=1  (browser-verify, then run cleanup with this username)")

