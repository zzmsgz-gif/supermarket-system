#!/usr/bin/env python3
# 端到端验证：订单/购物车 sku_spec 快照落库。
# 流程：注册临时买家 -> 登录 -> 带 skuSpec 加购 -> 断言购物车项 skuSpec
#      -> 建地址 -> 下单 -> 断言订单项 skuSpec -> 安全清理测试数据。
# 用法：python deploy/verify-sku-spec.py
import json
import subprocess
import time
import urllib.request
import urllib.error

BASE = "http://localhost:8080/api"
MYSQL = r"C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
DB = "supermarket_system"
DB_USER = "root"
DB_PASS = "zzmsgz"
ADMIN = ("admin", "123456")

SPEC = "color:red"  # 商品 35 (ceshi) 的 SKU 为 {"color":"red","size":"M"}
PRODUCT_ID = 35


def req(method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(r, timeout=15) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}


def auth_login(username, password):
    st, body = req("POST", "/auth/login", body={"username": username, "password": password})
    if st != 200 or body.get("code") != 0:
        raise SystemExit(f"login failed: {st} {body}")
    return body["data"]["token"]


def mysql_run(sql):
    cmd = [MYSQL, "-u", DB_USER, f"-p{DB_PASS}", DB, "-e", sql]
    subprocess.run(cmd, capture_output=True, text=True, check=False)


def main():
    ts = int(time.time())
    username = f"skuspec_{ts}"
    phone = f"13{ts % 1000000000:09d}"  # 11 位、第二位 3-9 的合法手机号
    password = "skuspec123"
    print(f"[setup] register buyer {username} phone={phone}")

    st, body = req("POST", "/auth/register",
                   body={"username": username, "password": password,
                         "phone": phone, "nickname": "sku_spec_tester"})
    if st != 200 or body.get("code") != 0:
        raise SystemExit(f"register failed: {st} {body}")
    token = body["data"]["token"]
    print("[setup] registered + logged in")

    # 1) 带 skuSpec 加购
    st, body = req("POST", "/cart/items",
                   token=token, body={"productId": PRODUCT_ID, "quantity": 1, "skuSpec": SPEC})
    assert st == 200 and body.get("code") == 0, f"addToCart failed: {st} {body}"
    print(f"[cart] addToCart ok (skuSpec={SPEC})")

    # 2) 断言购物车项 skuSpec
    st, body = req("GET", "/cart", token=token)
    assert st == 200 and body.get("code") == 0, f"getCart failed: {st} {body}"
    items = body["data"]["items"]
    assert items, "cart is empty after add"
    cart_item = next((i for i in items if i.get("productId") == PRODUCT_ID), None)
    assert cart_item, "cart item for product 35 not found"
    got = cart_item.get("skuSpec")
    assert got == SPEC, f"cart skuSpec mismatch: got={got!r} expect={SPEC!r}"
    print(f"[assert] cart item skuSpec == {got!r}  ✅")
    cart_item_id = cart_item["id"]

    # 3) 建收货地址
    st, body = req("POST", "/addresses", token=token,
                   body={"receiverName": "测试", "receiverPhone": phone,
                         "province": "北京", "city": "北京", "district": "海淀",
                         "detailAddress": "测试路1号", "isDefault": True})
    assert st == 200 and body.get("code") == 0, f"create address failed: {st} {body}"
    address_id = body["data"]["id"]
    print(f"[setup] address created id={address_id}")

    # 4) 下单
    st, body = req("POST", "/orders", token=token,
                   body={"cartItemIds": [cart_item_id], "addressId": address_id})
    assert st == 200 and body.get("code") == 0, f"createOrder failed: {st} {body}"
    order_id = body["data"]["id"]
    print(f"[order] created order id={order_id}")

    # 5) 断言订单项 skuSpec
    st, body = req("GET", f"/orders/{order_id}", token=token)
    assert st == 200 and body.get("code") == 0, f"getOrder failed: {st} {body}"
    oitems = body["data"].get("items") or []
    assert oitems, "order has no items"
    oitem = next((o for o in oitems if o.get("productId") == PRODUCT_ID), None)
    assert oitem, "order item for product 35 not found"
    ogot = oitem.get("skuSpec")
    assert ogot == SPEC, f"order item skuSpec mismatch: got={ogot!r} expect={SPEC!r}"
    print(f"[assert] order item skuSpec == {ogot!r}  ✅")

    # 6) 清理（FK 安全顺序）
    print("[cleanup] removing test data")
    req("DELETE", f"/cart/items/{cart_item_id}", token=token)
    req("DELETE", f"/addresses/{address_id}", token=token)
    mysql_run(f"DELETE FROM stock_log WHERE order_id = {order_id};")
    mysql_run(f"DELETE FROM order_item WHERE order_id = {order_id};")
    mysql_run(f"DELETE FROM orders WHERE id = {order_id};")

    # 软删除测试用户（需管理员令牌）
    admin_token = auth_login(*ADMIN)
    uid = find_user_id(username, admin_token)
    assert uid, f"test user {username} not found for cleanup"
    st, body = req("DELETE", f"/admin/users/{uid}", token=admin_token)
    print("[cleanup] done")

    print("\n✅ ALL CHECKS PASSED: sku_spec 已正确落库到 cart_item 与 order_item")


def find_user_id(username, admin_token):
    st, body = req("GET", "/admin/users?keyword=" + username + "&page=1&size=10", token=admin_token)
    users = (body.get("data") or {}).get("items") or []
    return next((u["id"] for u in users if u.get("username") == username), None)


if __name__ == "__main__":
    main()
