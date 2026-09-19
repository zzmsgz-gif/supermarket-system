"""秒杀「自动拆分」验证：超出每人限购的部分按原价，而不是拒绝。

设计变更（替代旧的「加购/改数量即拦 409」）：
  购物车阶段：允许把数量加到超过限购，超出部分按原价成交，不再拒绝加购；
  下单阶段：每件商品按「前 min(数量, 用户剩余秒杀名额) 件秒杀价 + 其余原价」拆成两行订单。

覆盖：
  A. 购物车：限购 2 + 加购 10 → flashQty=2、regularPrice 有值、小计=2*秒杀价+8*原价
  B. 下单：生成两行（2 件秒杀 + 8 件原价），金额恒等式成立，秒杀名额只扣 2
  C. 买满后（剩余 0）再加购 5 → 全部原价（flashQty=0），名额不再扣
  D. 不限购场次（perUserLimit=0）：加购 10 → 全部秒杀，名额扣 10
  E. 对账：接口「我的已购」== 手工 SQL；全库金额恒等式 0 条不一致

自建测试账号/场次，finally 清库（沿用 verify-flash-limit.py 的清理顺序与断环逻辑）。
"""
import json
import subprocess
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta

BASE = "http://localhost:8080/api"
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
DB = "supermarket_system"
DBPASS = "zzmsgz"
STAMP = str(int(time.time()))

buyer = "fsp_" + STAMP
admin = "fspadm_" + STAMP
PW = "Fsp12345"

LIMIT_PID = 79          # 大米（开秒杀、限购 2）
NOLIMIT_PID = 74       # 抽纸（开秒杀、不限购）

uid = admin_id = None
created_orders = []
created_sales = []
results = []


def request(method, path, body=None, token=None):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Content-Type": "application/json",
                 **({"Authorization": "Bearer " + token} if token else {})},
        method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode() or "null")
    except urllib.error.HTTPError as err:
        raw = err.read().decode()
        try:
            return err.code, json.loads(raw or "null")
        except ValueError:
            return err.code, {"raw": raw}


def call(method, path, body=None, token=None):
    status, payload = request(method, path, body, token)
    if status >= 400 or (isinstance(payload, dict) and payload.get("code") not in (0, None)):
        raise RuntimeError(f"{method} {path} -> {status} {payload}")
    return payload.get("data")


def status_of(method, path, body=None, token=None):
    return request(method, path, body, token)[0]


def sql(stmt):
    p = subprocess.run([MYSQL, "-uroot", "-p" + DBPASS, "--default-character-set=utf8mb4",
                        "-D", DB, "-N", "-B", "-e", stmt],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if p.returncode != 0:
        raise RuntimeError("SQL fail: " + p.stderr)
    return p.stdout.strip()


def check(name, cond, detail=""):
    results.append((name, bool(cond)))
    print(("PASS  " if cond else "FAIL  ") + name + (("  | " + str(detail)) if detail else ""))


def ts(hours_from_now):
    return (datetime.now() + timedelta(hours=hours_from_now)).strftime("%Y-%m-%dT%H:%M")


def cart_line(tok, product_id):
    cart = call("GET", "/cart", None, tok)
    return next((i for i in cart.get("items", []) if i["productId"] == product_id), None)


def clear_cart(tok):
    cart = call("GET", "/cart", None, tok)
    for item in cart.get("items", []):
        call("DELETE", f"/cart/items/{item['id']}", None, tok)


def sale_of(tok, sale_id):
    lst = call("GET", "/flash-sales", None, tok)
    return next((s for s in lst if s["id"] == sale_id), None)


def place_order(tok, address_id, product_id):
    cart = call("GET", "/cart", None, tok)
    ids = [i["id"] for i in cart.get("items", []) if i["productId"] == product_id]
    order = call("POST", "/orders", {"cartItemIds": ids, "addressId": address_id,
                                     "fulfillmentType": "DELIVERY"}, tok)
    created_orders.append(order["id"])
    return order


def order_items(tok, order_id):
    return call("GET", f"/orders/{order_id}", None, tok).get("items", [])


try:
    # ============ 准备 ============
    admin_tok = call("POST", "/auth/register",
                     {"username": admin, "password": PW, "nickname": "拆分管理员",
                      "phone": "137" + STAMP[-8:]})["token"]
    admin_id = call("GET", "/auth/me", None, admin_tok)["id"]
    sql(f"UPDATE {DB}.sys_user SET role='ADMIN' WHERE id={admin_id}")
    admin_tok = call("POST", "/auth/login", {"username": admin, "password": PW})["token"]

    tok = call("POST", "/auth/register",
               {"username": buyer, "password": PW, "nickname": "拆分买家",
                "phone": "138" + STAMP[-8:]})["token"]
    uid = call("GET", "/auth/me", None, tok)["id"]
    call("POST", "/wallet/recharges", {"amount": 1000}, tok)
    address = call("POST", "/addresses", {"receiverName": "拆分收", "receiverPhone": "138" + STAMP[-8:],
                                          "province": "广东省", "city": "深圳市", "district": "南山区",
                                          "detailAddress": "拆分路 1 号", "isDefault": True}, tok)
    address_id = call("GET", "/addresses", None, tok)[0]["id"]

    limit_price = float(sql(f"SELECT price FROM {DB}.product WHERE id={LIMIT_PID}"))
    nolimit_price = float(sql(f"SELECT price FROM {DB}.product WHERE id={NOLIMIT_PID}"))

    sale_limit = call("POST", "/admin/flash-sales", {
        "productId": LIMIT_PID, "name": "拆分限购_" + STAMP, "flashPrice": round(limit_price * 0.5, 2),
        "totalQuota": 50, "perUserLimit": 2, "startTime": ts(-1), "endTime": ts(24),
        "status": 1, "sortNo": 9}, admin_tok)
    created_sales.append(sale_limit["id"])
    sale_nolimit = call("POST", "/admin/flash-sales", {
        "productId": NOLIMIT_PID, "name": "拆分不限购_" + STAMP, "flashPrice": round(nolimit_price * 0.5, 2),
        "totalQuota": 50, "perUserLimit": 0, "startTime": ts(-1), "endTime": ts(24),
        "status": 1, "sortNo": 9}, admin_tok)
    created_sales.append(sale_nolimit["id"])

    fp = sale_limit["flashPrice"]

    # ============ A. 购物车拆分展示 ============
    call("POST", "/cart/items", {"productId": LIMIT_PID, "quantity": 10}, tok)
    line = cart_line(tok, LIMIT_PID)
    check("A1 加购 10 件（限购 2）不再被拒，购物车行数量=10",
          line and line["quantity"] == 10, f"qty={line['quantity'] if line else '-'}")
    check("A2 ⭐ 购物车行标出 flashQty=2（仅 2 件享秒杀价）",
          line and line.get("flashQty") == 2, f"flashQty={line.get('flashQty') if line else '-'}")
    check("A3 透出 regularPrice（超出部分按原价那段的价格）",
          line and line.get("regularPrice") is not None, f"regularPrice={line.get('regularPrice') if line else '-'}")
    expect_sub = round(2 * fp + 8 * float(line["regularPrice"]), 2)
    check("A4 ⭐ 小计 = 2*秒杀价 + 8*原价（金额恒等式不被拆分破坏）",
          line and abs(float(line["subtotalAmount"]) - expect_sub) < 0.01,
          f"subtotal={line['subtotalAmount'] if line else '-'} expect={expect_sub}")
    # productPrice*quantity == subtotalAmount 恒成立
    check("A5 productPrice*quantity == subtotalAmount（前端小计口径一致）",
          line and abs(float(line["productPrice"]) * line["quantity"] - float(line["subtotalAmount"])) < 0.01,
          f"prodP={line['productPrice']} sub={line['subtotalAmount']}")

    # ============ B. 下单拆分 ============
    order = place_order(tok, address_id, LIMIT_PID)
    items = [i for i in order_items(tok, order["id"]) if i["productId"] == LIMIT_PID]
    flash_items = [i for i in items if i.get("flashSaleId")]
    reg_items = [i for i in items if not i.get("flashSaleId")]
    check("B1 ⭐ 订单拆出两行（1 行秒杀 + 1 行原价）",
          len(items) == 2 and len(flash_items) == 1 and len(reg_items) == 1,
          f"total={len(items)} flash={len(flash_items)} reg={len(reg_items)}")
    check("B2 秒杀行 = 2 件，单价=秒杀价",
          flash_items and flash_items[0]["quantity"] == 2
          and abs(float(flash_items[0]["productPrice"]) - fp) < 0.01,
          f"qty={flash_items[0]['quantity'] if flash_items else '-'} price={flash_items[0]['productPrice'] if flash_items else '-'}")
    check("B3 原价行 = 8 件，单价=原价（非秒杀价）",
          reg_items and reg_items[0]["quantity"] == 8
          and abs(float(reg_items[0]["productPrice"]) - float(line["regularPrice"])) < 0.01,
          f"qty={reg_items[0]['quantity'] if reg_items else '-'} price={reg_items[0]['productPrice'] if reg_items else '-'}")
    item_sum = round(sum(float(i["subtotalAmount"]) for i in items), 2)
    check("B4 订单 total_amount == 两行小计之和（不含运费，因自提）",
          abs(float(order["totalAmount"]) - item_sum) < 0.01,
          f"total={order['totalAmount']} itemsum={item_sum}")
    check("B5 金额恒等式（订单层面）",
          abs(float(order["totalAmount"]) + float(order.get("freightAmount", 0))
              - float(order.get("discountAmount", 0)) - float(order.get("activityDiscount", 0))
              - float(order.get("memberDiscount", 0)) - float(order.get("pointsDiscount", 0))
              - float(order["payAmount"])) < 0.01,
          f"pay={order['payAmount']}")

    sold = int(sql(f"SELECT sold_quota FROM {DB}.flash_sale WHERE id={sale_limit['id']}"))
    check("B6 ⭐ 秒杀名额只扣 2 件（不是 10）",
          sold == 2, f"sold_quota={sold}")

    mine = sale_of(tok, sale_limit["id"])
    check("B7 买满后「还能买 0 件」", mine.get("myRemainingQuota") == 0,
          f"left={mine.get('myRemainingQuota')}")

    # ============ C. 买满后再加购 → 全部原价 ============
    clear_cart(tok)
    call("POST", "/cart/items", {"productId": LIMIT_PID, "quantity": 5}, tok)
    line2 = cart_line(tok, LIMIT_PID)
    check("C1 买满后再加 5 件：flashQty=0（全部原价）",
          line2 and line2.get("flashQty") == 0, f"flashQty={line2.get('flashQty') if line2 else '-'}")
    order2 = place_order(tok, address_id, LIMIT_PID)
    items2 = [i for i in order_items(tok, order2["id"]) if i["productId"] == LIMIT_PID]
    check("C2 订单只有 1 行（全部原价，不拆出秒杀行）",
          len(items2) == 1 and not items2[0].get("flashSaleId") and items2[0]["quantity"] == 5,
          f"rows={len(items2)} qty={items2[0]['quantity'] if items2 else '-'}")

    # ============ D. 不限购场次：全部秒杀 ============
    clear_cart(tok)
    call("POST", "/cart/items", {"productId": NOLIMIT_PID, "quantity": 10}, tok)
    linen = cart_line(tok, NOLIMIT_PID)
    check("D1 不限购场次加购 10 件：flashQty=10（整行秒杀）",
          linen and linen.get("flashQty") == 10, f"flashQty={linen.get('flashQty') if linen else '-'}")
    order3 = place_order(tok, address_id, NOLIMIT_PID)
    items3 = [i for i in order_items(tok, order3["id"]) if i["productId"] == NOLIMIT_PID]
    check("D2 不限购下单：仅 1 行、10 件、全部秒杀价",
          len(items3) == 1 and items3[0]["quantity"] == 10 and items3[0].get("flashSaleId"),
          f"rows={len(items3)} qty={items3[0]['quantity'] if items3 else '-'}")

    # ============ E. 对账 ============
    after = sale_of(tok, sale_limit["id"])
    sql_bought = int(sql(
        f"SELECT COALESCE(SUM(i.quantity),0) FROM {DB}.order_item i "
        f"JOIN {DB}.orders o ON o.id=i.order_id "
        f"WHERE i.flash_sale_id={sale_limit['id']} AND o.user_id={uid} "
        f"AND o.status NOT IN ('CANCELED','CLOSED')"))
    check("E1 接口「我的已购」与手工 SQL 对账一致（限购场次：已购 2）",
          after.get("myBoughtQuantity") == sql_bought == 2,
          f"接口={after.get('myBoughtQuantity')} SQL={sql_bought}")
    bad = int(sql(
        f"SELECT COUNT(*) FROM {DB}.orders WHERE total_amount+freight_amount"
        f"-discount_amount-activity_discount-member_discount-points_discount <> pay_amount"))
    check("E2 订单金额恒等式未被破坏（全库 0 条不一致）", bad == 0, f"{bad} 条")

finally:
    try:
        for sid in created_sales:
            sql(f"DELETE FROM {DB}.flash_sale WHERE id={sid}")
        ids = [i for i in (uid, admin_id) if i is not None]
        idlist = ",".join(str(i) for i in ids) or "NULL"
        olist = ",".join(str(i) for i in created_orders) or "NULL"
        if created_orders:
            sql(f"UPDATE {DB}.product p JOIN ("
                f"SELECT oi.product_id AS pid, SUM(oi.quantity) AS q FROM {DB}.order_item oi "
                f"JOIN {DB}.orders o ON o.id = oi.order_id "
                f"WHERE oi.order_id IN ({olist}) AND o.status NOT IN ('CANCELED','CLOSED') "
                f"GROUP BY oi.product_id) t ON t.pid=p.id "
                f"SET p.stock=p.stock+t.q, p.sales=GREATEST(p.sales-t.q,0)")
            sql(f"UPDATE {DB}.orders SET user_coupon_id=NULL WHERE id IN ({olist})")
        for t in ("product_review", "user_message", "wallet_transaction", "point_ledger", "user_favorite",
                  "price_alert", "cart_item", "user_address", "user_coupon"):
            sql(f"DELETE FROM {DB}.{t} WHERE user_id IN ({idlist})")
        if created_orders:
            for oid in created_orders:
                sql(f"DELETE FROM {DB}.order_item WHERE order_id={oid}")
                sql(f"DELETE FROM {DB}.payment_record WHERE order_id={oid}")
                sql(f"DELETE FROM {DB}.stock_log WHERE order_id={oid}")
            sql(f"DELETE FROM {DB}.orders WHERE id IN ({olist})")
        sql(f"DELETE FROM {DB}.sys_user WHERE id IN ({idlist})")
        print("CLEANUP_OK")
    except Exception as exc:  # noqa: BLE001
        print("CLEANUP_FAIL:", exc)

passed = sum(1 for _, ok in results if ok)
print(f"\n==== {passed}/{len(results)} 通过 ====")
for name, ok in results:
    if not ok:
        print("  未通过: " + name)
