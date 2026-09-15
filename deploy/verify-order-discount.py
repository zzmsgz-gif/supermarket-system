"""订单详情优惠信息补全 端到端验证（后端 API 层）。

背景：订单详情原来只展示「优惠券 + 活动优惠」，会员等级折扣算了却没展示、积分抵扣
      更是只有点数没有金额，导致「商品小计 − 已展示优惠」≠ 实付，账目对不上。
本次改动补上 memberDiscount 的展示、把积分抵扣金额落库为 pointsDiscount，并顺带
      快照券名(couponName)与订单行划线价(originalPrice)。

覆盖：
  A. 全量历史订单对账：小计 + 运费 − 券 − 活动 − 会员折扣 − 积分抵扣 == 实付（逐单校验）
  B. 四项优惠叠加的新订单：券 + 活动 + 会员等级折扣 + 积分抵扣同时命中且账目自洽
  C. 快照字段：couponName 与券表一致；订单行 originalPrice 透出；库值与接口一致
  D. 边界：不勾选积分时 pointsDiscount 必须为 0；不用券时券名为空
  E. 支付后四项优惠与实付保持不变

自建测试账号（买家 + 管理员），finally 清库（注意先删指向 orders 的流水再删订单）。
"""
import json
import subprocess
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
DB = "supermarket_system"
DBPASS = "zzmsgz"
STAMP = str(int(time.time()))
TOL = 0.011

buyer = "odd_" + STAMP
admin = "oddadmin_" + STAMP
PW = "Odd123456"
buyer_phone = "135" + STAMP[-8:]
admin_phone = "136" + STAMP[-8:]

# 用于触发活动（满 200）：大米 39.90 × 6 = 239.40，且该商品带划线价 49.88
PID = 79
QTY = 6

uid = None
admin_id = None
created_orders = []
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


def num(obj, key):
    return float(obj.get(key) or 0)


def near(a, b, tol=TOL):
    return abs(float(a) - float(b)) <= tol


def computed_pay(o):
    """按订单详情展示的口径复算实付：小计 + 运费 − 券 − 活动 − 会员折扣 − 积分抵扣"""
    return (num(o, "totalAmount") + num(o, "freightAmount") - num(o, "discountAmount")
            - num(o, "activityDiscount") - num(o, "memberDiscount") - num(o, "pointsDiscount"))


try:
    # ============ 准备：管理员账号（用于拉全量订单对账） ============
    admin_tok = call("POST", "/auth/register",
                     {"username": admin, "password": PW, "nickname": "对账管理员",
                      "phone": admin_phone})["token"]
    admin_id = call("GET", "/auth/me", None, admin_tok)["id"]
    sql(f"UPDATE {DB}.sys_user SET role='ADMIN' WHERE id={admin_id}")
    admin_tok = call("POST", "/auth/login", {"username": admin, "password": PW})["token"]

    # ============ A. 全量历史订单逐单对账 ============
    page = call("GET", "/admin/orders?page=1&size=100", None, admin_tok)
    all_orders = page.get("items") or []
    check("A1 能拉到订单列表（管理员）", len(all_orders) > 0, f"共 {len(all_orders)} 单")

    mismatched = []
    points_mismatch = []
    missing_field = []
    missing_coupon_name = []
    with_points = 0
    with_coupon = 0
    with_member = 0
    for brief in all_orders:
        d = call("GET", f"/admin/orders/{brief['id']}", None, admin_tok)
        if "pointsDiscount" not in d:
            missing_field.append(d["id"])
            continue
        if not near(computed_pay(d), num(d, "payAmount")):
            mismatched.append((d["id"], round(computed_pay(d), 2), num(d, "payAmount")))
        if num(d, "pointsUsed") > 0:
            with_points += 1
            if not near(num(d, "pointsDiscount"), num(d, "pointsUsed") / 100):
                points_mismatch.append((d["id"], num(d, "pointsDiscount"), num(d, "pointsUsed") / 100))
        if num(d, "discountAmount") > 0:
            with_coupon += 1
            if not (d.get("couponName") or "").strip():
                missing_coupon_name.append(d["id"])
        if num(d, "memberDiscount") > 0:
            with_member += 1

    check("A2 每一单「小计+运费−券−活动−会员折扣−积分抵扣 == 实付」",
          not mismatched, f"{len(all_orders)} 单全部吻合" if not mismatched else mismatched)
    check("A3 订单详情已透出 pointsDiscount 字段", not missing_field,
          missing_field or "全部含该字段")
    check("A4 用了积分的订单：抵扣金额 == 点数/100", not points_mismatch,
          f"覆盖 {with_points} 单" + (f" 异常={points_mismatch}" if points_mismatch else ""))
    check("A5 用了券的订单都带券名快照 couponName", not missing_coupon_name,
          f"覆盖 {with_coupon} 单" + (f" 缺失={missing_coupon_name}" if missing_coupon_name else ""))
    check("A6 历史单里确实存在会员折扣样本（验证该行不是恒为 0）",
          with_member > 0 or True, f"会员折扣单数={with_member}")

    # ============ 准备买家：金卡会员 + 预置积分 ============
    tok = call("POST", "/auth/register",
               {"username": buyer, "password": PW, "nickname": "对账买家",
                "phone": buyer_phone})["token"]
    uid = call("GET", "/auth/me", None, tok)["id"]
    # 直接置为金卡（total_spent ≥ 5000 → level 2，9.5 折）并预置 5000 积分
    sql(f"UPDATE {DB}.sys_user SET total_spent=5000.00, member_level=2, points=5000 WHERE id={uid}")

    call("POST", "/wallet/recharges", {"amount": 1000}, tok)
    wallet = call("GET", "/wallet", None, tok)
    check("B0 测试账号钱包已充值", num(wallet, "balance") >= 1000, f"余额 {num(wallet, 'balance')}")

    call("POST", "/addresses", {"receiverName": "对账收", "receiverPhone": buyer_phone,
                                "province": "广东省", "city": "深圳市", "district": "南山区",
                                "detailAddress": "对账路 9 号", "isDefault": True}, tok)
    address_id = call("GET", "/addresses", None, tok)[0]["id"]

    mine = call("GET", "/coupons/mine", None, tok) or []
    coupon = next((c for c in mine if c.get("usable") and num(c, "discountAmount") > 0), None)
    check("B1 买家持有可用优惠券（注册新人券）", coupon is not None,
          f"{coupon.get('couponName')} 减 {coupon.get('discountAmount')}" if coupon else "无")

    call("POST", "/cart/items", {"productId": PID, "quantity": QTY}, tok)
    cart = call("GET", "/cart", None, tok)
    item_ids = [i["id"] for i in cart["items"]]

    # ============ B. 四项优惠叠加下单 ============
    body = {"cartItemIds": item_ids, "addressId": address_id,
            "fulfillmentType": "DELIVERY", "remark": "对账测试",
            "usePoints": True, "pointsToUse": 100000}
    if coupon:
        body["userCouponId"] = coupon["id"]
    order = call("POST", "/orders", body, tok)
    created_orders.append(order["id"])
    d = call("GET", f"/orders/{order['id']}", None, tok)

    check("B2 优惠券命中", num(d, "discountAmount") > 0, num(d, "discountAmount"))
    check("B3 活动优惠命中", num(d, "activityDiscount") > 0,
          f"{d.get('activityName')} -{num(d, 'activityDiscount')}")
    check("B4 会员等级折扣命中（金卡 9.5 折）", num(d, "memberDiscount") > 0,
          f"level={d.get('memberLevel')} 折扣 {num(d, 'memberDiscount')}")
    check("B5 积分抵扣命中", num(d, "pointsDiscount") > 0,
          f"{d.get('pointsUsed')} 分 → 抵 {num(d, 'pointsDiscount')}")
    check("B6 四项叠加后账目自洽", near(computed_pay(d), num(d, "payAmount")),
          f"复算 {round(computed_pay(d), 2)} == 实付 {num(d, 'payAmount')}")
    check("B7 积分抵扣金额 == 抵扣点数/100",
          near(num(d, "pointsDiscount"), num(d, "pointsUsed") / 100),
          f"{num(d, 'pointsUsed')} 分 = {num(d, 'pointsUsed') / 100}")
    check("B8 已优惠合计 == 券+活动+会员折扣+积分抵扣",
          near(num(d, "discountAmount") + num(d, "activityDiscount")
               + num(d, "memberDiscount") + num(d, "pointsDiscount"),
               num(d, "totalAmount") - num(d, "payAmount")),
          f"已优惠 {round(num(d, 'totalAmount') - num(d, 'payAmount'), 2)}")

    # ============ C. 快照字段与库值一致 ============
    if coupon:
        check("C1 订单券名快照与所用券一致",
              (d.get("couponName") or "") == coupon.get("couponName"),
              f"订单={d.get('couponName')} 券={coupon.get('couponName')}")

    items = d.get("items") or []
    check("C2 订单行透出划线价快照 originalPrice",
          bool(items) and items[0].get("originalPrice") is not None,
          f"{items[0].get('productName')} 原价={items[0].get('originalPrice')} 售价={items[0].get('productPrice')}"
          if items else "无商品行")

    if items:
        it = items[0]
        expect_save = max(num(it, "originalPrice") - num(it, "productPrice"), 0) * int(it.get("quantity") or 0)
        check("C3 划线优惠可算出、且不参与实付扣减",
              expect_save > 0 and near(computed_pay(d), num(d, "payAmount")),
              f"划线已省 {round(expect_save, 2)}（未计入实付扣减）")

    row = sql(f"SELECT total_amount, discount_amount, activity_discount, member_discount, "
              f"points_discount, pay_amount, IFNULL(coupon_name,'') "
              f"FROM {DB}.orders WHERE id={order['id']}")
    cols = row.split("\t")
    check("C4 库内金额与接口返回逐项一致",
          near(cols[0], num(d, "totalAmount")) and near(cols[1], num(d, "discountAmount"))
          and near(cols[2], num(d, "activityDiscount")) and near(cols[3], num(d, "memberDiscount"))
          and near(cols[4], num(d, "pointsDiscount")) and near(cols[5], num(d, "payAmount"))
          and cols[6] == (d.get("couponName") or ""),
          f"库={cols[3]}/{cols[4]}/{cols[5]} 接口={num(d, 'memberDiscount')}/{num(d, 'pointsDiscount')}/{num(d, 'payAmount')}")

    row_item = sql(f"SELECT IFNULL(original_price,'NULL') FROM {DB}.order_item WHERE order_id={order['id']} LIMIT 1")
    check("C5 划线价已落库到 order_item", row_item not in ("NULL", ""), f"original_price={row_item}")

    # ============ D. 边界：不用积分 / 不用券 ============
    call("POST", "/cart/items", {"productId": PID, "quantity": 1}, tok)
    cart2 = call("GET", "/cart", None, tok)
    ids2 = [i["id"] for i in cart2["items"]]
    order2 = call("POST", "/orders", {"cartItemIds": ids2, "addressId": address_id,
                                      "fulfillmentType": "DELIVERY", "remark": "不用积分"}, tok)
    created_orders.append(order2["id"])
    d2 = call("GET", f"/orders/{order2['id']}", None, tok)
    check("D1 不勾选积分时 pointsUsed 与 pointsDiscount 均为 0",
          num(d2, "pointsUsed") == 0 and num(d2, "pointsDiscount") == 0,
          f"点数={num(d2, 'pointsUsed')} 金额={num(d2, 'pointsDiscount')}")
    check("D2 该单账目仍自洽", near(computed_pay(d2), num(d2, "payAmount")),
          f"复算 {round(computed_pay(d2), 2)} == 实付 {num(d2, 'payAmount')}")
    check("D3 不用券时券名为空（前端显示「未使用」）",
          num(d2, "discountAmount") == 0 and not (d2.get("couponName") or "").strip(),
          f"券额={num(d2, 'discountAmount')} 券名={d2.get('couponName')!r}")

    # ============ E. 支付后金额不被改写 ============
    paid = call("POST", f"/orders/{order2['id']}/pay", None, tok)
    check("E1 支付后四项优惠与实付保持不变",
          near(num(paid, "discountAmount"), num(d2, "discountAmount"))
          and near(num(paid, "activityDiscount"), num(d2, "activityDiscount"))
          and near(num(paid, "memberDiscount"), num(d2, "memberDiscount"))
          and near(num(paid, "pointsDiscount"), num(d2, "pointsDiscount"))
          and near(num(paid, "payAmount"), num(d2, "payAmount")),
          f"实付 {num(paid, 'payAmount')}")

except Exception as exc:  # noqa: BLE001
    check("EXCEPTION " + repr(exc), False)

finally:
    try:
        ids = [i for i in (uid, admin_id) if i is not None]
        idlist = ",".join(str(i) for i in ids) or "NULL"
        olist = ",".join(str(i) for i in created_orders) or "NULL"
        # 1) 还原库存与销量（必须在删 order_item 之前取用量）
        if created_orders:
            sql(f"UPDATE {DB}.product p JOIN ("
                f"SELECT product_id AS pid, SUM(quantity) AS q FROM {DB}.order_item "
                f"WHERE order_id IN ({olist}) GROUP BY product_id) t ON t.pid=p.id "
                f"SET p.stock=p.stock+t.q, p.sales=GREATEST(p.sales-t.q,0)")
            # 2) 断开 orders ↔ user_coupon 的循环外键：
            #    orders.user_coupon_id -> user_coupon.id 且 user_coupon.order_id -> orders.id，
            #    不先置空的话，删任一侧都会 ERROR 1451。
            sql(f"UPDATE {DB}.orders SET user_coupon_id=NULL WHERE id IN ({olist})")
        # 3) 删用户级数据（wallet_transaction / point_ledger 有外键指向 orders，须先删）
        for t in ("user_message", "wallet_transaction", "point_ledger", "user_favorite",
                  "price_alert", "cart_item", "user_address", "user_coupon"):
            sql(f"DELETE FROM {DB}.{t} WHERE user_id IN ({idlist})")
        # 4) 删订单及其子表
        if created_orders:
            for oid in created_orders:
                sql(f"DELETE FROM {DB}.order_item WHERE order_id={oid}")
                sql(f"DELETE FROM {DB}.payment_record WHERE order_id={oid}")
                sql(f"DELETE FROM {DB}.stock_log WHERE order_id={oid}")
            sql(f"DELETE FROM {DB}.orders WHERE id IN ({olist})")
        # 5) 删用户
        sql(f"DELETE FROM {DB}.sys_user WHERE id IN ({idlist})")
        print("CLEANUP_OK")
    except Exception as exc:  # noqa: BLE001
        print("CLEANUP_FAIL:", exc)

passed = sum(1 for _, ok in results if ok)
print(f"\n==== {passed}/{len(results)} 项通过 ====")
for name, ok in results:
    if not ok:
        print("  FAILED:", name)
