"""三种履约方式（同城即时配送 / 快递配送 / 门店自提）端到端验证 —— 后端 API 层。

覆盖：
  A. 三种履约都能下单成功，且落库的 fulfillment_type 正确
  B. 快递运费：商品小计 < ¥99 收 ¥8；≥ ¥99 免运费
  C. ⚠️ 运费门槛按**商品小计**判定，不是按优惠后的应付 —— 用券把小计 102.4 打到应付 97.4，
     运费仍必须是 0（这是最容易被写错的一条）
  D. 金额口径恒等式：total + freight − 券 − activity − member − points = pay（三单都成立）
  E. 只有「同城即时配送」保存配送时段；快递配送传了时段也不落库
  F. 门店自提：生成自提码；「备货完成」→ 待取货(SHIPPED) 且**不写任何物流字段**
  G. ⚠️ 自提单不允许走 /ship（防止再出现"自提单被迫瞎填快递单号"）
  H. 老客户端兼容：fulfillmentType 传旧的 'DELIVERY' 落库为 INSTANT
  I. 未登录下单被拒(401)

自建测试账号与订单，finally 中按外键顺序清理（stock_log 有 order_id / operator_id 两条外键，
必须在删 orders 与删 sys_user 之前清掉）。跑完核对库存回到脚本开始前的值。
"""
import json
import math
import subprocess
import time
import urllib.error
import urllib.request
from _db import DBUSER, DBPASS

BASE = "http://localhost:8080/api"
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
STAMP = str(int(time.time()))
results = []

buyer = "fft_" + STAMP
PW = "Ffttest123"
phone = "135" + STAMP[-8:]
uid = None
created_orders = []
ORIGINAL_STOCK = {}
MAX_LOG_ID = None


def call(method, path, body=None, token=None):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Content-Type": "application/json",
                 **({"Authorization": "Bearer " + token} if token else {})},
        method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode()).get("data"), resp.status
    except urllib.error.HTTPError as err:
        raise RuntimeError(f"{method} {path} -> {err.code} {err.read().decode()}")


def call_status(method, path, body=None, token=None):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Content-Type": "application/json",
                 **({"Authorization": "Bearer " + token} if token else {})},
        method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as err:
        return err.code, err.read().decode()


def sql(stmt):
    p = subprocess.run([MYSQL, "-u", DBUSER, "-p" + DBPASS, "-D", "supermarket_system",
                        "--default-character-set=utf8mb4", "-N", "-B", "-e", stmt],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if p.returncode != 0:
        raise RuntimeError("SQL fail: " + p.stderr)
    return p.stdout.strip()


def scalar(stmt):
    out = sql(stmt)
    return out.splitlines()[0].strip() if out else ""


def check(name, cond, detail=""):
    results.append((name, bool(cond)))
    print(("PASS  " if cond else "FAIL  ") + name + (("  | " + str(detail)) if detail else ""))


def num(value):
    return float(value or 0)


def identity_ok(order):
    """金额口径契约：total + freight − 券 − activity − member − points == pay"""
    left = (num(order["totalAmount"]) + num(order.get("freightAmount"))
            - num(order.get("discountAmount"))
            - num(order.get("activityDiscount"))
            - num(order.get("memberDiscount"))
            - num(order.get("pointsDiscount")))
    return abs(left - num(order["payAmount"])) < 0.011


def place(items, token, fulfillment_type, **extra):
    """按 [{productId, quantity}] 加购后下单，返回订单响应。"""
    for item in items:
        call("POST", "/cart/items", item, token)
    cart, _ = call("GET", "/cart", None, token)
    ids = [i["id"] for i in cart["items"]]
    body = {"cartItemIds": ids, "fulfillmentType": fulfillment_type, **extra}
    order, _ = call("POST", "/orders", body, token)
    created_orders.append(order["id"])
    return order


try:
    # ⚠️ 必须在造任何数据之前记录：stock_log 没有 user_id，只能按 id 截断来清（见 finally）
    MAX_LOG_ID = int(scalar("SELECT COALESCE(MAX(id),0) FROM supermarket_system.stock_log") or 0)

    reg = call("POST", "/auth/register", {"username": buyer, "password": PW,
                                          "nickname": "履约验证", "phone": phone})[0]
    tok = reg["token"]
    uid = call("GET", "/auth/me", None, tok)[0]["id"]
    admin_tok = call("POST", "/auth/login", {"username": "admin", "password": "Admin123456"})[0]["token"]
    check("A0 准备：买家 + 管理员 token", bool(tok) and bool(admin_tok))

    call("POST", "/wallet/recharges", {"amount": 2000}, tok)
    balance = num(call("GET", "/wallet", None, tok)[0]["balance"])
    check("A1 充值 2000 到账", balance >= 2000, balance)

    products, _ = call("GET", "/products?page=1&size=50", None, tok)
    products = products["items"]
    cheap = next(p for p in products if num(p["price"]) < 10 and p["stock"] > 5)     # 用于凑小计 <99
    pricey = next(p for p in products if num(p["price"]) > 8 and p["stock"] > 20)    # 用于凑小计 ≥99
    ORIGINAL_STOCK[cheap["id"]] = int(cheap["stock"])
    ORIGINAL_STOCK[pricey["id"]] = int(pricey["stock"])
    stores, _ = call("GET", "/stores", None, tok)
    store = stores[0]
    slots, _ = call("GET", "/delivery-slots", None, tok)
    slot = slots[0]["value"] if slots else ""
    # 即时配送与快递配送都必须有收货地址（门店自提不需要）
    address, _ = call("POST", "/addresses", {
        "receiverName": "履约验证", "receiverPhone": phone, "province": "广东省",
        "city": "深圳市", "district": "福田区", "detailAddress": "验证路 1 号", "isDefault": True}, tok)
    addr_id = address["id"]
    check("A2 准备：商品 / 门店 / 配送时段 / 收货地址就绪",
          bool(cheap) and bool(store) and bool(slot) and bool(addr_id))

    # ---------- 快递配送：小计 < 99 收 ¥8 ----------
    qty_cheap = max(1, int(80 // num(cheap["price"])))  # 凑到 80 左右，确保 < 99
    o_express_low = place([{"productId": cheap["id"], "quantity": qty_cheap}], tok, "EXPRESS", addressId=addr_id)
    check("B1 快递单落库 fulfillment_type=EXPRESS", o_express_low["fulfillmentType"] == "EXPRESS",
          o_express_low["fulfillmentType"])
    check("B2 小计 < ¥99 时收运费 ¥8（小计 %.2f）" % num(o_express_low["totalAmount"]),
          num(o_express_low["totalAmount"]) < 99 and num(o_express_low["freightAmount"]) == 8,
          o_express_low["freightAmount"])
    check("B3 快递单的应付 = 小计 + 运费（无其他优惠）",
          abs(num(o_express_low["payAmount"]) - num(o_express_low["totalAmount"]) - 8) < 0.011,
          o_express_low["payAmount"])
    check("B4 金额恒等式成立（快递/低额）", identity_ok(o_express_low))

    # ---------- 快递配送：小计 ≥ 99 免运费 ----------
    qty_pricey = max(1, int(105 // num(pricey["price"])) + 1)
    o_express_high = place([{"productId": pricey["id"], "quantity": qty_pricey}], tok, "EXPRESS", addressId=addr_id)
    check("B5 小计 ≥ ¥99 时免运费（小计 %.2f）" % num(o_express_high["totalAmount"]),
          num(o_express_high["totalAmount"]) >= 99 and num(o_express_high["freightAmount"]) == 0,
          o_express_high["freightAmount"])
    check("B6 金额恒等式成立（快递/免运费）", identity_ok(o_express_high))

    # ---------- ⭐ 运费门槛按商品小计，而不是优惠后的应付 ----------
    # ⚠️ call() 返回 (data, status) 元组：取列表必须解包，别写 [0]（那样拿到的是整个元组的第 0 项）
    mine_list, _ = call("GET", "/coupons/mine", None, tok)
    held = {uc["couponId"] for uc in mine_list}
    available_list, _ = call("GET", "/coupons/available", None, tok)
    available = [c for c in available_list if c["id"] not in held]
    check("C0 有可领取的优惠券用于本用例", len(available) >= 1, f"{len(available)} 张")
    coupon = min(available, key=lambda c: num(c["thresholdAmount"]))
    mine, _ = call("POST", f"/coupons/{coupon['id']}/receive", None, tok)
    # 精确构造临界单：挑一个单价 < 券面额的商品，数量取 ceil(99/单价)，
    # 小计必落在 [99, 99+单价) ⊂ [99, 104)，减去券额后必然跌破 99
    tiny = next(p for p in products
                if num(p["price"]) < num(coupon["discountAmount"]) and p["stock"] > 60)
    qty_tiny = math.ceil(99 / num(tiny["price"]))
    ORIGINAL_STOCK[tiny["id"]] = int(tiny["stock"])
    o_express_coupon = place([{"productId": tiny["id"], "quantity": qty_tiny}], tok, "EXPRESS",
                             addressId=addr_id, userCouponId=mine["id"])
    goods = num(o_express_coupon["totalAmount"])
    pay = num(o_express_coupon["payAmount"])
    check("C1 构成临界单：小计 ≥ ¥99 而用券后应付 < ¥99（小计 %.2f → 应付 %.2f）" % (goods, pay),
          goods >= 99 and pay < 99, f"小计 {goods} 应付 {pay}")
    check("C2 ⭐ 运费门槛看商品小计 → 仍免运费", num(o_express_coupon["freightAmount"]) == 0,
          o_express_coupon["freightAmount"])
    check("C3 券确实抵扣了", num(o_express_coupon["discountAmount"]) > 0,
          o_express_coupon["discountAmount"])
    check("C4 金额恒等式成立（快递/用券）", identity_ok(o_express_coupon))

    # ---------- 同城即时配送：不收费、时段落库 ----------
    o_instant = place([{"productId": cheap["id"], "quantity": 1}], tok, "INSTANT", addressId=addr_id, deliverySlot=slot)
    check("D1 即时配送落库 fulfillment_type=INSTANT", o_instant["fulfillmentType"] == "INSTANT",
          o_instant["fulfillmentType"])
    check("D2 即时配送不收运费", num(o_instant["freightAmount"]) == 0, o_instant["freightAmount"])
    check("D3 即时配送保存配送时段", o_instant.get("deliverySlot") == slot, o_instant.get("deliverySlot"))
    check("D4 金额恒等式成立（即时配送）", identity_ok(o_instant))

    # ---------- 快递配送即使传了时段也不该落库 ----------
    o_express_slot = place([{"productId": cheap["id"], "quantity": 1}], tok, "EXPRESS", addressId=addr_id, deliverySlot=slot)
    check("D5 快递配送不保存配送时段（时效由第三方决定）",
          not o_express_slot.get("deliverySlot"), o_express_slot.get("deliverySlot"))

    # ---------- 门店自提 ----------
    o_pickup = place([{"productId": cheap["id"], "quantity": 1}], tok, "PICKUP", pickupStoreId=store["id"])
    check("E1 自提单落库 fulfillment_type=PICKUP", o_pickup["fulfillmentType"] == "PICKUP",
          o_pickup["fulfillmentType"])
    check("E2 自提单不收运费", num(o_pickup["freightAmount"]) == 0, o_pickup["freightAmount"])
    check("E3 自提单生成自提码", bool(o_pickup.get("pickupCode")), o_pickup.get("pickupCode"))
    check("E4 自提单记录门店快照", o_pickup.get("pickupStoreName") == store["name"],
          o_pickup.get("pickupStoreName"))
    check("E5 金额恒等式成立（自提）", identity_ok(o_pickup))

    # ---------- H. 老客户端兼容：旧的 DELIVERY 落库为 INSTANT ----------
    st, _ = call_status("POST", "/cart/items", {"productId": cheap["id"], "quantity": 1}, tok)
    cart, _ = call("GET", "/cart", None, tok)
    legacy, _ = call("POST", "/orders", {"cartItemIds": [i["id"] for i in cart["items"]],
                                        "fulfillmentType": "DELIVERY", "addressId": addr_id}, tok)
    created_orders.append(legacy["id"])
    check("H1 旧值 DELIVERY 兼容落库为 INSTANT", legacy["fulfillmentType"] == "INSTANT",
          legacy["fulfillmentType"])

    # ---------- 支付后才可发货 ----------
    for oid in [o_instant["id"], o_pickup["id"], o_express_low["id"]]:
        call("POST", f"/orders/{oid}/pay", None, tok)

    # ---------- G. 自提单不允许走 /ship ----------
    st, body = call_status("POST", f"/admin/orders/{o_pickup['id']}/ship",
                           {"shipCompany": "顺丰速运", "shipNo": "SF" + STAMP}, admin_tok)
    check("G1 自提单走 /ship 被拒(409)", st == 409, f"{st} {body[:80]}")

    # ---------- F. 自提「备货完成」：不填物流也能推进，且不写物流字段 ----------
    ready, _ = call("POST", f"/admin/orders/{o_pickup['id']}/ready", {}, admin_tok)
    check("F1 自提「备货完成」成功，状态为 SHIPPED（=待取货）", ready["status"] == "SHIPPED", ready["status"])
    check("F2 备货完成不产生任何物流字段",
          not ready.get("shipNo") and not ready.get("shipCompany"),
          f"{ready.get('shipCompany')}/{ready.get('shipNo')}")
    check("F3 库里 ship_no 仍为空",
          scalar(f"SELECT IFNULL(ship_no,'NULL') FROM supermarket_system.orders WHERE id={o_pickup['id']}") == "NULL")

    # ---------- 即时配送走 /ship（用户选择与快递共用「单号」表单）----------
    shipped, _ = call("POST", f"/admin/orders/{o_instant['id']}/ship",
                      {"shipCompany": "美团配送", "shipNo": "MT" + STAMP}, admin_tok)
    check("G2 即时配送可发货并写入单号",
          shipped["status"] == "SHIPPED" and shipped.get("shipNo") == "MT" + STAMP,
          f"{shipped['status']}/{shipped.get('shipNo')}")

    # ---------- I. 未登录下单 ----------
    st, _ = call_status("POST", "/orders", {"cartItemIds": [1], "fulfillmentType": "INSTANT"})
    check("I1 未登录下单被拒(401)", st == 401, st)

    # ---------- 校验：库里三种取值都存在且无 DELIVERY 残留 ----------
    stale = scalar(f"SELECT COUNT(*) FROM supermarket_system.orders WHERE id IN "
                   f"({','.join(str(i) for i in created_orders)}) AND fulfillment_type='DELIVERY'")
    check("J1 新建订单里没有 DELIVERY 残留", stale == "0", stale)

except Exception as exc:  # noqa: BLE001
    check("EXCEPTION " + repr(exc), False)

finally:
    try:
        if uid is not None:
            # 还原库存：把被本脚本改过的商品按记录值写回（比逐单回退更稳，不会重复计数）
            for pid, qty in ORIGINAL_STOCK.items():
                sql(f"UPDATE supermarket_system.product SET stock = {qty} WHERE id = {pid}")
            sql(f"UPDATE supermarket_system.orders SET user_coupon_id=NULL WHERE user_id={uid}")
            for t in ("product_review", "wallet_transaction", "user_message", "point_ledger",
                      "user_favorite", "price_alert", "cart_item", "user_address", "user_coupon"):
                sql(f"DELETE FROM supermarket_system.{t} WHERE user_id={uid}")
            # ⚠️ stock_log 有两条外键（order_id / operator_id），必须在删 orders 与删 sys_user 之前清
            sql(f"DELETE FROM supermarket_system.stock_log WHERE id > {MAX_LOG_ID}")
            sql(f"DELETE oi FROM supermarket_system.order_item oi JOIN supermarket_system.orders o "
                f"ON o.id=oi.order_id WHERE o.user_id={uid}")
            sql(f"DELETE pr FROM supermarket_system.payment_record pr JOIN supermarket_system.orders o "
                f"ON o.id=pr.order_id WHERE o.user_id={uid}")
            sql(f"DELETE FROM supermarket_system.orders WHERE user_id={uid}")
            sql(f"DELETE FROM supermarket_system.sys_user WHERE id={uid}")
            left_u = scalar(f"SELECT COUNT(*) FROM supermarket_system.sys_user WHERE id={uid}")
            left_o = scalar(f"SELECT COUNT(*) FROM supermarket_system.orders WHERE user_id={uid}")
            check("K1 清理后无残留账号", left_u == "0", left_u)
            check("K2 清理后无残留订单", left_o == "0", left_o)
        print("CLEANUP_OK")
    except Exception as exc:  # noqa: BLE001
        print("CLEANUP_FAIL:", exc)

passed = sum(1 for _, ok in results if ok)
print(f"\n==== {passed}/{len(results)} 项通过 ====")
for name, ok in results:
    if not ok:
        print("  FAILED:", name)
