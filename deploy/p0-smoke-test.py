"""P0 feature smoke test for the supermarket system."""
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
    proc = subprocess.run(
        [MYSQL, "-uroot", "-pzzmsgz", "-N", "-B", "-e", statement],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    if proc.returncode != 0:
        raise RuntimeError("SQL failed: " + proc.stderr)
    return proc.stdout.strip()


def check(name, ok, detail=""):
    results.append((name, ok, detail))
    print(("PASS " if ok else "FAIL ") + name + (" :: " + str(detail) if detail else ""))


buyer = "p0buyer_" + STAMP
admin = "p0admin_" + STAMP

# 1. register + recharge
tok = call("POST", "/auth/register", {"username": buyer, "password": "P0test123", "nickname": "P0买家"})["token"]
check("注册买家账号", bool(tok))
call("POST", "/wallet/recharges", {"amount": 300}, tok)
wallet = call("GET", "/wallet", None, tok)
check("钱包充值 300", float(wallet["balance"]) == 300, wallet["balance"])

# 2. receive a coupon (满30减5, unlimited)
available = call("GET", "/coupons/available", None, tok)
check("查询可领取优惠券", len(available) >= 1, f"{len(available)} 张")
coupon = min(available, key=lambda item: float(item["thresholdAmount"]))
mine = call("POST", f"/coupons/{coupon['id']}/receive", None, tok)
check("领取优惠券", mine["status"] == "UNUSED", mine["couponName"])

# 3. cart + address
products = call("GET", "/products?page=1&size=20", None, tok)["items"]
product = next(p for p in products if p["stock"] > 10)
call("POST", "/cart/items", {"productId": product["id"], "quantity": 10}, tok)
cart = call("GET", "/cart", None, tok)
address = call("POST", "/addresses", {
    "receiverName": "张三", "receiverPhone": "13800000000", "province": "广东省",
    "city": "深圳市", "district": "南山区", "detailAddress": "科技园 1 号", "isDefault": True,
}, tok)
check("创建收货地址", bool(address.get("id")))

# 4. usable coupon + create order with coupon
usable = call("GET", f"/coupons/usable?amount={cart['selectedAmount']}", None, tok)
check("结算时可用优惠券", len(usable) >= 1, f"订单金额 {cart['selectedAmount']}，可用 {len(usable)} 张")
user_coupon = usable[0]
order = call("POST", "/orders", {
    "addressId": address["id"],
    "cartItemIds": [item["id"] for item in cart["items"]],
    "userCouponId": user_coupon["id"],
    "remark": "P0 冒烟测试",
}, tok)
expected = round(float(cart["selectedAmount"]) - float(user_coupon["discountAmount"]), 2)
check("下单使用优惠券", abs(float(order["discountAmount"]) - float(user_coupon["discountAmount"])) < 0.01
      and abs(float(order["payAmount"]) - expected) < 0.01,
      f"原价 {order['totalAmount']} 优惠 {order['discountAmount']} 应付 {order['payAmount']}")
check("优惠券状态变为已使用",
      call("GET", "/coupons/mine", None, tok)[0]["status"] == "USED")

# 5. pay
paid = call("POST", f"/orders/{order['id']}/pay", None, tok)
check("余额支付订单", paid["status"] == "PAID", paid["status"])

# 6. admin: promote a second account then ship
call("POST", "/auth/register", {"username": admin, "password": "P0test123", "nickname": "P0管理员"})
sql(f"UPDATE supermarket_system.sys_user SET role='ADMIN' WHERE username='{admin}'")
admin_tok = call("POST", "/auth/login", {"username": admin, "password": "P0test123"})["token"]
shipped = call("POST", f"/admin/orders/{order['id']}/ship",
               {"shipCompany": "顺丰速运", "shipNo": "SF" + STAMP}, admin_tok)
check("管理端带单号发货", shipped["status"] == "SHIPPED" and shipped["shipNo"] == "SF" + STAMP,
      f"{shipped['shipCompany']} {shipped['shipNo']}")

# 7. user applies refund -> admin approves
applied = call("POST", f"/orders/{order['id']}/refund-apply", {"reason": "商品破损"}, tok)
check("用户申请退款", applied["refundStatus"] == "APPLYING")
pending = call("GET", "/admin/orders?refundStatus=APPLYING", None, admin_tok)
check("后台按退款状态筛选", any(o["id"] == order["id"] for o in pending["items"]),
      f"{len(pending['items'])} 条待处理")
refunded = call("POST", f"/admin/orders/{order['id']}/refund-review",
                {"approved": True, "remark": "已核实，同意退款"}, admin_tok)
check("后台同意退款", refunded["status"] == "CLOSED" and refunded["refundStatus"] == "APPROVED"
      and refunded["paymentStatus"] == "REFUNDED", f"{refunded['status']}/{refunded['paymentStatus']}")
balance_after = float(call("GET", "/wallet", None, tok)["balance"])
check("退款金额回到钱包", abs(balance_after - 300) < 0.01, balance_after)
stock_now = int(sql(f"SELECT stock FROM supermarket_system.product WHERE id={product['id']}"))
check("退款后库存回滚", stock_now == product["stock"],
      f"下单前 {product['stock']}，退款后 {stock_now}")

# 8. review flow: order -> pay -> ship -> confirm -> review
call("POST", "/cart/items", {"productId": product["id"], "quantity": 2}, tok)
cart2 = call("GET", "/cart", None, tok)
order2 = call("POST", "/orders", {
    "addressId": address["id"],
    "cartItemIds": [item["id"] for item in cart2["items"]],
}, tok)
call("POST", f"/orders/{order2['id']}/pay", None, tok)
call("POST", f"/admin/orders/{order2['id']}/ship",
     {"shipCompany": "京东物流", "shipNo": "JD" + STAMP}, admin_tok)
completed = call("POST", f"/orders/{order2['id']}/confirm-receipt", None, tok)
check("用户确认收货", completed["status"] == "COMPLETED")
reviews = call("POST", f"/reviews/orders/{order2['id']}",
               {"rating": 5, "content": "新鲜，配送很快"}, tok)
check("完成订单可评价", len(reviews) == len(order2["items"]), f"{len(reviews)} 条评价")
public_reviews = call("GET", f"/reviews/products/{product['id']}?page=1&size=10", None, tok)
check("商品评价公开查询", len(public_reviews["items"]) >= 1, f"{public_reviews['total']} 条")
try:
    call("POST", f"/reviews/orders/{order2['id']}", {"rating": 4, "content": "重复评价"}, tok)
    check("同一订单不可重复评价", False, "未拒绝")
except RuntimeError:
    check("同一订单不可重复评价", True, "已返回 409")

# 9. timeout close: backdate an unpaid order and wait for the scheduler
call("POST", "/cart/items", {"productId": product["id"], "quantity": 1}, tok)
cart3 = call("GET", "/cart", None, tok)
order3 = call("POST", "/orders", {
    "addressId": address["id"],
    "cartItemIds": [item["id"] for item in cart3["items"]],
}, tok)
stock_before = int(sql(f"SELECT stock FROM supermarket_system.product WHERE id={product['id']}"))
sql(f"UPDATE supermarket_system.orders SET created_at = NOW() - INTERVAL 31 MINUTE WHERE id = {order3['id']}")
print("   等待定时任务扫描（约 70 秒）...")
time.sleep(75)
closed = call("GET", f"/orders/{order3['id']}", None, tok)
check("超时未支付订单自动关闭", closed["status"] == "CLOSED", f"{closed['status']} closedAt={closed.get('closedAt')}")
stock_after = int(sql(f"SELECT stock FROM supermarket_system.product WHERE id={product['id']}"))
check("关单后库存释放", stock_after == stock_before + 1, f"{stock_before} -> {stock_after}")

# 10. stock alerts
sql(f"UPDATE supermarket_system.product SET stock = 4 WHERE id = {product['id']}")
alerts = call("GET", "/admin/stock-alerts", None, admin_tok)
check("库存预警列表", any(a["id"] == product["id"] for a in alerts),
      f"{len(alerts)} 个低库存商品")
call("POST", f"/admin/products/{product['id']}/stock-adjustments",
     {"changeQuantity": 50, "bizType": "PURCHASE", "remark": "冒烟测试补货"}, admin_tok)
check("补货后移出预警", not any(a["id"] == product["id"]
                              for a in call("GET", "/admin/stock-alerts", None, admin_tok)))

passed = sum(1 for _, ok, _ in results if ok)
print(f"\n===== {passed}/{len(results)} 项通过 =====")
for name, ok, detail in results:
    if not ok:
        print("  FAILED:", name, detail)
