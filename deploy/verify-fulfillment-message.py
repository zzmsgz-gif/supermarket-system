"""履约（门店自提 + 配送时段）+ 消息中心 端到端验证（后端 API 层）。

覆盖：
  A. 公开履约接口：/stores 只返回营业门店、/delivery-slots 生成时段、/admin/stores 需 ADMIN
  B. 门店 CRUD：新增/重名 409/编辑/停业/删除，停业后前台不可选
  C. 门店自提下单：缺门店 400、门店不存在 400、停业门店 400、成功自提（门店名/自提码/免地址）
  D. 配送下单：带配送时段成功、缺 addressId 400
  E. 消息中心：注册欢迎消息、未读数、类型筛选、单条已读、全部已读、支付消息去重
  F. 会员升级消息：累计消费跨过阈值时推送

自建测试账号 + 临时门店，finally 中清库（删除订单/消息/门店/用户，还原商品库存）。
"""
import json
import subprocess
import time
import urllib.error
import urllib.request
from _db import DBUSER, DBPASS

BASE = "http://localhost:8080/api"
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
STAMP = str(int(time.time()))
results = []

buyer = "fulv_" + STAMP
admin = "fuladmin_" + STAMP
PW = "Fulv12345"
buyer_phone = "132" + STAMP[-8:]
admin_phone = "133" + STAMP[-8:]
store_name = "验证门店_" + STAMP
store_name2 = "验证门店B_" + STAMP

uid = None
admin_id = None
created_stores = []
created_orders = []
ordered_qty = 0
pid = None


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
    p = subprocess.run([MYSQL, "-u", DBUSER, "-p" + DBPASS, "--default-character-set=utf8mb4",
                        "-D", "supermarket_system", "-N", "-B", "-e", stmt],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if p.returncode != 0:
        raise RuntimeError("SQL fail: " + p.stderr)
    return p.stdout.strip()


def check(name, cond, detail=""):
    results.append((name, bool(cond)))
    print(("PASS  " if cond else "FAIL  ") + name + (("  | " + str(detail)) if detail else ""))


def near(a, b, tol=0.011):
    return abs(float(a) - float(b)) <= tol


try:
    # ============ A. 公开履约接口 ============
    status, payload = request("GET", "/stores")
    stores_public = payload.get("data") or []
    check("A1 /stores 免登录可访问", status == 200, status)
    check("A2 /stores 只返回营业门店", all(int(s["status"]) == 1 for s in stores_public) and len(stores_public) >= 3,
          [s["name"] for s in stores_public])

    status, payload = request("GET", "/delivery-slots")
    slots = payload.get("data") or []
    check("A3 /delivery-slots 免登录可访问且有可选时段", status == 200 and len(slots) > 0, len(slots))
    if slots:
        check("A4 时段带 value/label/date", all(k in slots[0] for k in ("value", "label", "date")), slots[0])
        check("A5 时段至少提前 1 小时（不含已过期窗口）",
              all(s["date"] > time.strftime("%Y-%m-%d") or s["value"] > time.strftime("%Y-%m-%d %H:%M")
                  for s in slots[:2]), slots[0]["value"])

    tok = call("POST", "/auth/register", {"username": buyer, "password": PW,
                                          "nickname": "履约验证", "phone": buyer_phone})["token"]
    uid = call("GET", "/auth/me", None, tok)["id"]
    check("A6 普通用户访问 /admin/stores 被拒", status_of("GET", "/admin/stores", None, tok) == 403)

    areg = call("POST", "/auth/register", {"username": admin, "password": PW,
                                           "nickname": "履约验证管理员", "phone": admin_phone})
    admin_id = call("GET", "/auth/me", None, areg["token"])["id"]
    sql(f"UPDATE supermarket_system.sys_user SET role='ADMIN' WHERE username='{admin}'")
    atok = call("POST", "/auth/login", {"username": admin, "password": PW})["token"]

    # ============ B. 门店 CRUD ============
    created = call("POST", "/admin/stores", {
        "name": store_name, "address": "深圳市南山区验证路 1 号", "phone": "0755-0000 0001",
        "businessHours": "08:00-22:00", "city": "深圳市", "district": "南山区",
        "pickupNotice": "验证用门店，凭自提码取货。", "status": 1, "sortNo": 5,
    }, atok)
    created_stores.append(created["id"])
    check("B1 新增门店成功", created["name"] == store_name and created["status"] == 1)
    check("B2 新增返回 id 与 createdAt（DB 默认值已回读）",
          created.get("id") and created.get("createdAt"), created.get("createdAt"))

    check("B3 门店重名返回 409", status_of("POST", "/admin/stores", {
        "name": store_name, "address": "重复地址"}, atok) == 409)

    updated = call("PUT", f"/admin/stores/{created['id']}", {
        "name": store_name, "address": "深圳市南山区验证路 2 号", "businessHours": "09:00-21:00",
        "city": "深圳市", "district": "南山区", "status": 1, "sortNo": 7,
    }, atok)
    check("B4 编辑门店生效", updated["address"].endswith("2 号") and updated["sortNo"] == 7, updated["address"])

    stopped = call("PATCH", f"/admin/stores/{created['id']}/status", {"status": 0}, atok)
    check("B5 停业门店状态变更", stopped["status"] == 0)
    public_now = call("GET", "/stores")
    check("B6 停业门店不再出现在前台列表",
          all(s["id"] != created["id"] for s in public_now))

    # ============ C. 门店自提下单 ============
    call("POST", "/wallet/recharges", {"amount": 1000}, tok)
    prods = call("GET", "/products?page=1&size=20", None, tok)["items"]
    prod = next(p for p in prods if p["stock"] > 10)
    pid = prod["id"]

    def add_to_cart(qty=2):
        call("POST", "/cart/items", {"productId": pid, "quantity": qty}, tok)
        return [i["id"] for i in call("GET", "/cart", None, tok)["items"]]

    ids = add_to_cart()
    check("C1 自提缺门店返回 400",
          status_of("POST", "/orders", {"cartItemIds": ids, "fulfillmentType": "PICKUP"}, tok) == 400)
    check("C2 自提门店不存在返回 400",
          status_of("POST", "/orders", {"cartItemIds": ids, "fulfillmentType": "PICKUP",
                                        "pickupStoreId": 99999999}, tok) == 400)
    check("C3 自提停业门店返回 400",
          status_of("POST", "/orders", {"cartItemIds": ids, "fulfillmentType": "PICKUP",
                                        "pickupStoreId": created["id"]}, tok) == 400)

    # 恢复营业后自提下单（不传 addressId，也不建收货地址）
    call("PATCH", f"/admin/stores/{created['id']}/status", {"status": 1}, atok)
    pickup = call("POST", "/orders", {"cartItemIds": ids, "fulfillmentType": "PICKUP",
                                      "pickupStoreId": created["id"]}, tok)
    created_orders.append(pickup["id"])
    ordered_qty += 2
    check("C4 自提下单成功且无需收货地址", pickup["id"] is not None)
    check("C5 订单履约方式为 PICKUP", pickup["fulfillmentType"] == "PICKUP", pickup["fulfillmentType"])
    check("C6 记录门店快照", pickup["pickupStoreName"] == store_name, pickup["pickupStoreName"])
    check("C7 生成自提码（订单号后 6 位）",
          pickup["pickupCode"] and pickup["orderNo"].endswith(pickup["pickupCode"]),
          (pickup["pickupCode"], pickup["orderNo"]))
    check("C8 自提运费为 0", near(pickup["freightAmount"], 0), pickup["freightAmount"])

    call("POST", f"/orders/{pickup['id']}/pay", None, tok)
    pdetail = call("GET", f"/orders/{pickup['id']}", None, tok)
    check("C9 支付后自提信息仍在", pdetail["pickupCode"] == pickup["pickupCode"]
          and pdetail["pickupStoreName"] == store_name)

    msgs = call("GET", "/messages?page=1&size=20", None, tok)
    paid_msg = next((m for m in msgs["items"] if m["title"] == "订单已支付成功"), None)
    check("C10 支付成功推送订单消息", paid_msg is not None,
          [m["title"] for m in msgs["items"]])
    if paid_msg:
        check("C11 自提单消息含自提码", pickup["pickupCode"] in (paid_msg["content"] or ""),
              paid_msg["content"])
        check("C12 消息可跳转订单详情",
              paid_msg["linkView"] == "orderDetail" and paid_msg["linkRef"] == str(pickup["id"]),
              (paid_msg["linkView"], paid_msg["linkRef"]))
    dup = sql(f"SELECT COUNT(*) FROM supermarket_system.user_message WHERE user_id={uid} "
              f"AND dedupe_key='ORDER:PAID:{pickup['id']}'")
    check("C13 支付消息去重键唯一", dup == "1", dup)

    # ============ D. 配送下单 ============
    call("POST", "/addresses", {"receiverName": "王五", "receiverPhone": "13700000000",
                                "province": "广东省", "city": "深圳市", "district": "福田区",
                                "detailAddress": "验证路 9 号", "isDefault": True}, tok)
    aid = call("GET", "/addresses", None, tok)[0]["id"]

    ids2 = add_to_cart()
    check("D1 送货上门缺地址返回 400",
          status_of("POST", "/orders", {"cartItemIds": ids2, "fulfillmentType": "DELIVERY"}, tok) == 400)

    slot_value = slots[0]["value"]
    delivery = call("POST", "/orders", {"cartItemIds": ids2, "fulfillmentType": "DELIVERY",
                                        "addressId": aid, "deliverySlot": slot_value}, tok)
    created_orders.append(delivery["id"])
    ordered_qty += 2
    check("D2 送货上门下单成功", delivery["fulfillmentType"] == "DELIVERY", delivery["fulfillmentType"])
    check("D3 配送时段已保存", delivery["deliverySlot"] == slot_value, delivery["deliverySlot"])
    check("D4 送货单无自提码", not delivery.get("pickupCode"), delivery.get("pickupCode"))

    ids3 = add_to_cart()
    legacy = call("POST", "/orders", {"cartItemIds": ids3, "addressId": aid}, tok)
    created_orders.append(legacy["id"])
    ordered_qty += 2
    check("D5 不传 fulfillmentType 默认按送货上门（老客户端兼容）",
          legacy["fulfillmentType"] == "DELIVERY", legacy["fulfillmentType"])

    # ============ E. 消息中心 ============
    allmsgs = call("GET", "/messages?page=1&size=20", None, tok)
    check("E1 注册即收到欢迎消息",
          any(m["title"].startswith("欢迎加入") and m["type"] == "SYSTEM" for m in allmsgs["items"]),
          [m["title"] for m in allmsgs["items"]])

    orders_only = call("GET", "/messages?type=ORDER&page=1&size=20", None, tok)
    check("E2 按类型筛选只返回订单消息",
          len(orders_only["items"]) >= 1 and all(m["type"] == "ORDER" for m in orders_only["items"]),
          [m["type"] for m in orders_only["items"]])

    unread = call("GET", "/messages/unread-count", None, tok)
    check("E3 未读消息数 > 0", int(unread["count"]) > 0, unread["count"])

    first_unread = next((m for m in allmsgs["items"] if not m["isRead"]), None)
    if first_unread:
        call("POST", f"/messages/{first_unread['id']}/read", None, tok)
        after = call("GET", "/messages/unread-count", None, tok)
        check("E4 单条已读后未读数减 1",
              int(after["count"]) == int(unread["count"]) - 1, (after["count"], unread["count"]))
    else:
        check("E4 单条已读后未读数减 1", False, "没有未读消息可测")

    marked = call("POST", "/messages/read", None, tok)
    check("E5 全部已读返回更新条数", int(marked["updated"]) >= 1, marked["updated"])
    check("E6 全部已读后未计数为 0",
          int(call("GET", "/messages/unread-count", None, tok)["count"]) == 0)

    # ============ F. 会员升级消息 ============
    sql(f"UPDATE supermarket_system.sys_user SET total_spent=999, member_level=0 WHERE id={uid}")
    ids4 = add_to_cart()
    order4 = call("POST", "/orders", {"cartItemIds": ids4, "addressId": aid,
                                      "fulfillmentType": "DELIVERY"}, tok)
    created_orders.append(order4["id"])
    ordered_qty += 2
    call("POST", f"/orders/{order4['id']}/pay", None, tok)
    me = call("GET", "/auth/me", None, tok)
    check("F1 支付后累计消费跨过阈值升到银卡", int(me["memberLevel"]) >= 1, me["memberLevel"])
    member_msgs = call("GET", "/messages?type=MEMBER&page=1&size=20", None, tok)
    check("F2 升级推送会员消息",
          any("银卡" in m["title"] for m in member_msgs["items"]),
          [m["title"] for m in member_msgs["items"]])

except Exception as exc:  # noqa: BLE001
    check("EXCEPTION " + repr(exc), False)

finally:
    try:
        if pid is not None and ordered_qty:
            sql(f"UPDATE supermarket_system.product SET stock=stock+{ordered_qty}, "
                f"sales=GREATEST(sales-{ordered_qty},0) WHERE id={pid}")
        ids = [i for i in (uid, admin_id) if i is not None]
        idlist = ",".join(str(i) for i in ids) or "NULL"
        for t in ("user_message", "wallet_transaction", "point_ledger", "user_favorite",
                  "price_alert", "cart_item", "user_address", "user_coupon"):
            sql(f"DELETE FROM supermarket_system.{t} WHERE user_id IN ({idlist})")
        for oid in created_orders:
            sql(f"DELETE FROM supermarket_system.order_item WHERE order_id={oid}")
            sql(f"DELETE FROM supermarket_system.payment_record WHERE order_id={oid}")
            sql(f"DELETE FROM supermarket_system.stock_log WHERE order_id={oid}")
            sql(f"DELETE FROM supermarket_system.orders WHERE id={oid}")
        sql(f"DELETE FROM supermarket_system.sys_user WHERE id IN ({idlist})")
        # 临时门店：先软删后硬删，避免留下验证数据
        sql(f"DELETE FROM supermarket_system.store WHERE name IN ('{store_name}', '{store_name2}')")
        print("CLEANUP_OK")
    except Exception as exc:  # noqa: BLE001
        print("CLEANUP_FAIL:", exc)

passed = sum(1 for _, ok in results if ok)
print(f"\n==== {passed}/{len(results)} 项通过 ====")
for name, ok in results:
    if not ok:
        print("  FAILED:", name)
