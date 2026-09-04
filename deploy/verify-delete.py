import json, urllib.request, urllib.error

BASE = "http://localhost:8080/api"

def req(method, path, token=None, body=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=10) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {"raw": e.read().decode()}

# 1. login as admin
s, j = req("POST", "/auth/login", body={"username": "admin", "password": "123456"})
assert s == 200 and j.get("code") == 0, f"login failed: {s} {j}"
token = j["data"]["token"]
print("login OK, role =", j["data"].get("role"))

# 2. get a category id
s, j = req("GET", "/categories")
cats = j.get("data") or []
assert cats, "no categories"
cat_id = cats[0]["id"]
print("using categoryId =", cat_id, cats[0].get("name"))

# 3. create a temp product
sku = "TMP-DELETE-TEST"
s, j = req("POST", "/admin/products", token, {
    "categoryId": cat_id,
    "sku": sku,
    "name": "临时删除测试商品",
    "subtitle": "auto-test",
    "description": "created by verify-delete.py",
    "coverUrl": "",
    "price": 1.0,
    "originalPrice": 2.0,
    "stock": 10,
    "lowStockThreshold": 3,
    "unit": "件",
    "status": "ON_SALE",
})
assert s == 200 and j.get("code") == 0, f"create failed: {s} {j}"
pid = j["data"]["id"]
print("created product id =", pid, "status =", j["data"].get("status"))

# 4. confirm it appears in admin list
s, j = req("GET", "/admin/products", token)
items = j.get("data", {}).get("items") if isinstance(j.get("data"), dict) else j.get("data")
items = items or []
found = [p for p in items if p["id"] == pid]
print("appears in admin list before delete:", bool(found))

# 5. delete it
s, j = req("DELETE", f"/admin/products/{pid}", token)
print("DELETE status =", s, "code =", j.get("code"), "msg =", j.get("message"))
assert s == 200 and j.get("code") == 0, f"delete failed: {s} {j}"

# 6. confirm it's gone from admin list (soft-delete filters NOT_DELETED)
s, j = req("GET", "/admin/products", token)
items = j.get("data", {}).get("items") if isinstance(j.get("data"), dict) else j.get("data")
items = items or []
found = [p for p in items if p["id"] == pid]
print("appears in admin list after delete:", bool(found))
assert not found, "product still in list after delete!"

# 7. confirm public shop list also excludes it
s, j = req("GET", "/products")
shop = j.get("data", {}).get("items") if isinstance(j.get("data"), dict) else j.get("data")
shop = shop or []
print("appears in public shop list after delete:", any(p["id"] == pid for p in shop))

print("\nRESULT: real delete round-trip PASSED ✅")
