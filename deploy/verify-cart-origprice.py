import json
import time
import urllib.request
import urllib.error

BASE = "http://localhost:8080/api"
TS = int(time.time())
BUYER = f"cartck_{TS}"
PWD = "Test@1234"
ADMIN = "admin"
APWD = "123456"


def req(method, path, token=None, body=None, raw=False):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=15) as resp:
            text = resp.read().decode()
            return resp.status, (text if raw else json.loads(text))
    except urllib.error.HTTPError as e:
        text = e.read().decode()
        return e.code, (text if raw else json.loads(text))


def login(username, password):
    s, j = req("POST", "/auth/login", body={"username": username, "password": password})
    if s == 200 and j.get("code") == 0:
        return j["data"]["token"]
    raise SystemExit(f"login failed {username}: {s} {j}")


print("== register temp buyer ==")
s, j = req("POST", "/auth/register",
           body={"username": BUYER, "password": PWD, "nickname": "ck", "role": "BUYER"})
print("register:", s, j.get("code"))

buyer_token = login(BUYER, PWD)

print("== pick a product with originalPrice > price ==")
s, j = req("GET", "/products?page=1&size=50")
items = (j.get("data") or {}).get("items") or []
target = None
for p in items:
    op = p.get("originalPrice")
    pr = p.get("price")
    if op is not None and pr is not None and float(op) > float(pr):
        target = p
        break
if not target:
    target = items[0] if items else None
print("target product:", target["id"], "price=", target["price"], "original=", target.get("originalPrice"))

print("== add to cart ==")
s, j = req("POST", "/cart/items", token=buyer_token,
           body={"productId": target["id"], "quantity": 2})
print("add:", s, j.get("code"))

print("== GET /cart -> check productOriginalPrice ==")
s, j = req("GET", "/cart", token=buyer_token)
cart = j.get("data") or {}
cart_items = cart.get("items") or []
found = None
for it in cart_items:
    if it.get("productId") == target["id"]:
        found = it
        break
print("cart item:", json.dumps(found, ensure_ascii=False))
assert found is not None, "item not in cart"
op = found.get("productOriginalPrice")
pr = found.get("productPrice")
print("productOriginalPrice=", op, "productPrice=", pr)
assert op is not None, "productOriginalPrice missing in cart response!"
assert float(op) >= float(pr), "originalPrice should be >= price"
print("PASS: cart response carries productOriginalPrice and is >= price")

print("== cleanup temp buyer ==")
admin_token = login(ADMIN, APWD)
# resolve buyer id by username, then delete by id
s, j = req("GET", f"/admin/users?keyword={BUYER}&page=1&size=10", token=admin_token)
users = (j.get("data") or {}).get("items") or []
uid = None
for u in users:
    if u.get("username") == BUYER:
        uid = u.get("id")
        break
print("resolved buyer id:", uid)
if uid:
    s, j = req("DELETE", f"/admin/users/{uid}", token=admin_token)
    print("delete buyer:", s, j.get("code"))
print("DONE")
