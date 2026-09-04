import json
import time
import urllib.request
import urllib.error

BASE = "http://127.0.0.1:8080/api"
STAMP = str(int(time.time()))[-6:]


def req(method, path, body=None, token=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=15) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}


# 1) 尝试用种子 admin 登录
token = None
for pw in ["admin123", "admin", "123456", "admin888", "password", "Admin123"]:
    s, j = req("POST", "/auth/login", {"username": "admin", "password": pw})
    if s == 200 and (j.get("token") or (j.get("data") or {}).get("token")):
        token = j.get("token") or (j.get("data") or {}).get("token")
        print("LOGIN_OK password=%s" % pw)
        break
if not token:
    print("NO_ADMIN_LOGIN")
    raise SystemExit(1)

# 2) 取一个分类 id
s, j = req("GET", "/categories")
if isinstance(j, dict):
    data = j.get("data")
    cats = data if isinstance(data, list) else (data or {}).get("items") or []
else:
    cats = j if isinstance(j, list) else []
cat_id = cats[0]["id"] if cats else 1
print("category_id=%s" % cat_id)

# 3) 非法：原价 < 现价  -> 应被拒绝
s, j = req("POST", "/admin/products", {
    "categoryId": cat_id, "sku": "PVBAD" + STAMP, "name": "价格校验-非法",
    "price": 10, "originalPrice": 5, "stock": 10, "unit": "件", "status": "ON_SALE",
}, token)
print("INVALID_CASE", s, j)
assert s != 200, "非法用例竟成功创建，规则未生效"
assert "原价不能低于现价" in str(j), j
print("PASS: 原价<现价 被拦截 -> %s" % j.get("message", j))

# 4) 合法：原价 > 现价  -> 应创建成功且回显 originalPrice
s, j = req("POST", "/admin/products", {
    "categoryId": cat_id, "sku": "PVOK" + STAMP, "name": "价格校验-合法",
    "price": 10, "originalPrice": 20, "stock": 10, "unit": "件", "status": "ON_SALE",
}, token)
print("VALID_CASE", s, j)
assert s == 200, "合法用例创建失败: %s" % j
prod = j.get("data") or {}
assert str(prod.get("originalPrice")) in ("20", "20.00", "20.0"), prod
print("PASS: 原价>现价 创建成功，回显 originalPrice=%s" % prod.get("originalPrice"))

# 5) 清理：删除刚创建的测试商品（软删除）
pid = prod.get("id")
if pid:
    s, j = req("DELETE", "/admin/products/%s" % pid, token=token)
    print("CLEANUP delete product", s)

print("ALL_PRICE_VALIDATION_TESTS_PASSED")
