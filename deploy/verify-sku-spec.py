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
from _db import DBPASS as DB_PASS
SPEC = "color:red"  # 自由文本规格串，用于验证规格随购物车行/订单行落库
PRODUCT_ID = None  # 运行时动态选取（见 main() 里 setup 段）；曾经写死成 35，那个商品早已不存在


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



def mysql_run(sql):
    # -N -B：去掉表头与表格边框，让 SELECT 的输出可以直接当标量读（mysql_scalar 依赖这点）
    cmd = [MYSQL, "-u", DB_USER, f"-p{DB_PASS}", DB, "-N", "-B",
           "--default-character-set=utf8mb4", "-e", sql]
    p = subprocess.run(cmd, capture_output=True, text=True,
                       encoding="utf-8", errors="replace", check=False)
    return p.stdout.strip()


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

    # 商品 id 改为运行时选取：原来写死的 PRODUCT_ID=35 那个商品早已不存在，会让脚本永远跑不过。
    # skuSpec 是自由文本（落 cart_item.sku_spec），任何在售且有余量的商品都能验证「规格随行落库」。
    global PRODUCT_ID
    st, body = req("GET", "/products?page=1&size=50", token=token)
    assert st == 200 and body.get("code") == 0, f"list products failed: {st} {body}"
    PRODUCT_ID = next(p["id"] for p in body["data"]["items"] if p["stock"] > 3)
    print(f"[setup] using product {PRODUCT_ID}")

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
                         # ⚠️ 必须落在即时配送范围内（门店服务区域），否则下单会被范围校验挡在业务断言之前
                         "province": "广东省", "city": "深圳市", "district": "南山区",
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

    # 删除测试用户
    # ⚠️ 这里原来是「管理员登录 + 软删用户」，但 ADMIN 口令写错（123456），登录必然 401 ——
    #    于是每跑一次都残留一个测试账号。改为按项目统一约定直接 SQL 硬删：
    #    先清 user 级流水（注册会自动发新人券与积分，删用户前必须先删 user_coupon），
    #    再删 sys_user；顺带核对删干净了。
    uid = mysql_scalar(f"SELECT id FROM sys_user WHERE username = '{username}' LIMIT 1")
    if uid:
        for table in ("product_review", "wallet_transaction", "user_message", "point_ledger",
                      "user_favorite", "price_alert", "cart_item", "user_address", "user_coupon"):
            mysql_run(f"DELETE FROM {table} WHERE user_id = {uid};")
        mysql_run(f"DELETE FROM sys_user WHERE id = {uid};")
        left = mysql_scalar(f"SELECT COUNT(*) FROM sys_user WHERE id = {uid}")
        assert left == "0", f"cleanup failed: user {uid} still present"
    print("[cleanup] done")
    print("\n✅ ALL CHECKS PASSED: sku_spec 已正确落库到 cart_item 与 order_item")


def mysql_scalar(sql):
    out = mysql_run(sql)
    return out.splitlines()[0].strip() if out else ""




if __name__ == "__main__":
    main()
