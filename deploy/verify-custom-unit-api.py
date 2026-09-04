import json, urllib.request, urllib.error

BASE = "http://localhost:8080/api"

def req(method, path, token=None, body=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    if token:
        r.add_header("Authorization", "Bearer " + token)
    if data:
        r.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(r, timeout=20) as resp:
            text = resp.read().decode()
    except urllib.error.HTTPError as e:
        text = e.read().decode()
    return json.loads(text) if text else None

# 1) 登录管理员
login = req("POST", "/auth/login", body={"username": "admin", "password": "123456"})
token = login["data"]["token"]
print("login code:", login.get("code"))

# 2) 取一个分类 id
cats = req("GET", "/admin/categories", token=token)
cat_data = cats["data"]
cat_id = cat_data[0]["id"] if isinstance(cat_data, list) else cat_data["items"][0]["id"]
print("categoryId:", cat_id)

# 3) 创建带自定义单位「箱」的商品
sku = "CUST-APITEST-1"
create = req("POST", "/admin/products", token=token, body={
    "sku": sku, "name": "自定义单位API验证", "price": 19.9, "stock": 50,
    "categoryId": cat_id, "unit": "箱", "status": "ON_SALE",
})
created = create["data"]
print("create code:", create.get("code"), "returned unit:", created.get("unit"))
pid = created.get("id")

# 4) 读回校验
got = req("GET", f"/admin/products/{pid}", token=token)["data"]
print("read-back unit:", got.get("unit"), "name:", got.get("name"))

# 5) 清理
d = req("DELETE", f"/admin/products/{pid}", token=token)
print("delete code:", d.get("code"))

ok = (created.get("unit") == "箱") and (got.get("unit") == "箱")
print("=== RESULT ===", json.dumps({"created_unit": created.get("unit"), "read_unit": got.get("unit"), "pass": ok}))
