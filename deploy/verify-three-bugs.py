#!/usr/bin/env python3
# 验证三个 bug 修复：订单详情含商品 / 评价创建并展示 / 库存上限校验
import urllib.request, urllib.error, json, subprocess, random, string, sys

BASE = "http://localhost:8080/api"
MYSQL = "C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
DB, DBUSER, DBPASS = "supermarket_system", "root", "zzmsgz"

def mysql(sql):
    r = subprocess.run([MYSQL, "-u", DBUSER, f"-p{DBPASS}", "--default-character-set=utf8mb4",
                        DB, "-e", sql], capture_output=True, text=True)
    return (r.stdout + r.stderr).replace("mysql: [Warning] Using a password on the command line interface can be insecure.\n", "")

def req(method, path, token=None, body=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=15) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {"raw": e.read().decode()}

def register(username, password, phone, nickname):
    code, resp = req("POST", "/auth/register", body={"username": username, "password": password, "phone": phone, "nickname": nickname})
    assert resp.get("code") == 0, f"register {code} {resp}"
    return resp["data"]["token"]

def gen_phone():
    return "13" + "".join(random.choices(string.digits, k=9))

PASS = []
def check(name, cond, extra=""):
    PASS.append((name, cond, extra))
    print(("PASS " if cond else "FAIL ") + name + (f"  -> {extra}" if extra else ""))

def main():
    suffix = "".join(random.choices(string.digits, k=8))
    user = f"bugfix_{suffix}"
    phone = gen_phone()
    tok = register(user, "Test123456", phone, "修复测试")
    print("registered", user, "token_len", len(tok))
    out = mysql(f"SELECT id FROM sys_user WHERE username='{user}';")
    uid = next((ln.strip() for ln in out.split("\n") if ln.strip().isdigit()), None)
    assert uid, f"uid not found: {out!r}"
    mysql(f"UPDATE sys_user SET balance=1000000 WHERE id={uid};")

    # 选一个有库存的在售商品
    code, resp = req("GET", "/products?page=1&size=50")
    data = resp.get("data") or {}
    items = data["items"] if isinstance(data, dict) and "items" in data else (data if isinstance(data, list) else [])
    prod = next((p for p in items if (p.get("stock") or 0) >= 2 and p.get("status") == "ON_SALE"), None)
    assert prod, "no suitable product"
    pid = prod["id"]
    orig_stock = int(prod.get("stock") or 0)
    print("product", pid, "orig_stock", orig_stock)

    # ---- Bug1: 订单详情应含购买商品 ----
    req("POST", "/cart/items", token=tok, body={"productId": pid, "quantity": 1})
    code, resp = req("GET", "/cart", token=tok)
    cart_items = (resp.get("data") or {}).get("items") or []
    assert cart_items, "cart empty"
    cart_id = cart_items[0]["id"]
    code, resp = req("POST", "/addresses", token=tok, body={
        "receiverName": "测", "receiverPhone": gen_phone(),
        "province": "省", "city": "市", "district": "区", "detailAddress": "路1号", "isDefault": True})
    addr_id = resp["data"]["id"]
    code, resp = req("POST", "/orders", token=tok, body={"cartItemIds": [cart_id], "addressId": addr_id, "remark": "bugfix"})
    assert resp.get("code") == 0, f"createOrder {resp}"
    oid = resp["data"]["id"]
    req("POST", f"/orders/{oid}/pay", token=tok)
    mysql(f"UPDATE orders SET status='COMPLETED', shipped_at=NOW(), completed_at=NOW() WHERE id={oid};")

    code, resp = req("GET", f"/orders/{oid}", token=tok)
    od = resp.get("data") or {}
    items1 = od.get("items") or []
    check("Bug1-用户订单详情含商品", len(items1) >= 1 and items1[0].get("productName") and items1[0].get("quantity") == 1,
          f"items={len(items1)} name={items1[0].get('productName') if items1 else None}")

    # ---- Bug2: 评价创建后能在商品详情页查到 ----
    code, resp = req("POST", f"/reviews/orders/{oid}", token=tok, body={"rating": 5, "content": f"bugfix-review-{suffix}"})
    created = resp.get("code") == 0
    check("Bug2-评价创建成功", created, f"code={code} msg={resp.get('message')}")
    code, resp = req("GET", f"/reviews/products/{pid}?page=1&size=20")
    revs = (resp.get("data") or {}).get("items") or []
    found = any((r.get("content") or "").startswith(f"bugfix-review-{suffix}") for r in revs)
    check("Bug2-商品详情页可查到评价", found, f"total_reviews={len(revs)}")

    # ---- Bug3: 库存=5 时禁止加购/改数量为 6 ----
    mysql(f"UPDATE product SET stock=5 WHERE id={pid};")
    code, resp = req("POST", "/cart/items", token=tok, body={"productId": pid, "quantity": 6})
    check("Bug3-加购6(库存5)被拒", code == 409, f"code={code} msg={resp.get('message')}")
    code, resp = req("POST", "/cart/items", token=tok, body={"productId": pid, "quantity": 5})
    assert resp.get("code") == 0, f"add 5 failed {resp}"
    code, resp = req("GET", "/cart", token=tok)
    ci = next((c for c in ((resp.get('data') or {}).get('items') or []) if c["productId"] == pid), None)
    assert ci, "cart item missing after add 5"
    code, resp = req("PUT", f"/cart/items/{ci['id']}", token=tok, body={"quantity": 6, "selected": True})
    check("Bug3-改数量到6(库存5)被拒", code == 409, f"code={code} msg={resp.get('message')}")
    mysql(f"UPDATE product SET stock={orig_stock} WHERE id={pid};")
    code, resp = req("PUT", f"/cart/items/{ci['id']}", token=tok, body={"quantity": 5, "selected": True})
    check("Bug3-改数量到5(库存5)成功", code == 200, f"code={code}")

    # ---- 清理（按外键从子到父顺序，避免残留）----
    mysql(f"DELETE FROM stock_log WHERE order_id={oid};")
    mysql(f"DELETE FROM payment_record WHERE order_id={oid};")
    mysql(f"DELETE FROM wallet_transaction WHERE user_id={uid};")
    mysql(f"DELETE FROM product_review WHERE order_id={oid};")
    mysql(f"DELETE FROM order_item WHERE order_id={oid};")
    mysql(f"DELETE FROM orders WHERE id={oid};")
    mysql(f"DELETE FROM cart_item WHERE user_id={uid};")
    mysql(f"DELETE FROM user_address WHERE user_id={uid};")
    mysql(f"DELETE FROM user_coupon WHERE user_id={uid};")
    mysql(f"DELETE FROM sys_user WHERE id={uid};")
    mysql(f"UPDATE product SET stock={orig_stock} WHERE id={pid};")

    total = len(PASS)
    ok = sum(1 for _, c, _ in PASS if c)
    print(f"\n===== {ok}/{total} 通过 =====")
    for n, c, e in PASS:
        print(("  OK  " if c else " FAIL ") + n + (f"  {e}" if e else ""))
    sys.exit(0 if ok == total else 1)

if __name__ == "__main__":
    main()
