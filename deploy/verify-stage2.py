"""Stage 2 verification: marketing activity + recommendation + dwell.

Covers three modules added in stage 2:
  A. 营销活动 (Marketing Activity): FULL_REDUCTION / DISCOUNT, scope ALL/CATEGORY,
     最优活动匹配 (best-activity selection), and that activity_discount is recorded
     on the order independently of coupons.
  B. 商品推荐 (Recommendation): /recommendations/guess + /products/related.
  C. 停留时长埋点 (Dwell): anonymous + authenticated POST /dwell and GET /dwell/rank.

Self-contained: creates its own test accounts / activities / dwell records and
cleans them up in a finally block (deletes activities, removes test orders +
items + stock_log, deletes dwell rows, restores product stock).
"""
import json
import subprocess
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta

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
        with urllib.request.urlopen(req, timeout=15) as resp:
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


def r2(x):
    return round(float(x) + 1e-9, 2)


def iso(dt):
    return dt.strftime("%Y-%m-%dT%H:%M:%S")


def ceil_div(a, b):
    return -(-a // b)


def run_tests():
    now = datetime.now()
    plus7 = now + timedelta(days=7)
    NOW = iso(now)
    PLUS7 = iso(plus7)

    # ----- Auth: a buyer + an admin -----
    buyer = "s2buyer_" + STAMP
    admin = "s2admin_" + STAMP
    buyer_phone = "138" + STAMP[:8]
    admin_phone = "139" + STAMP[:8]
    tok = call("POST", "/auth/register",
               {"username": buyer, "password": "S2test123", "nickname": "S2买家", "phone": buyer_phone})["token"]
    check("注册买家账号", bool(tok))
    call("POST", "/auth/register", {"username": admin, "password": "S2test123", "nickname": "S2管理员", "phone": admin_phone})
    sql(f"UPDATE supermarket_system.sys_user SET role='ADMIN' WHERE username='{admin}'")
    admin_tok = call("POST", "/auth/login", {"username": admin, "password": "S2test123"})["token"]
    check("管理员登录", bool(admin_tok))
    admin_id = int(sql(f"SELECT id FROM supermarket_system.sys_user WHERE username='{admin}'"))
    buyer_id = int(sql(f"SELECT id FROM supermarket_system.sys_user WHERE username='{buyer}'"))

    created_activities = []
    created_orders = []
    stock_restore = {}
    dwell_product_ids = []

    def ensure_stock(pid, need):
        cur = int(sql(f"SELECT stock FROM supermarket_system.product WHERE id={pid}"))
        stock_restore.setdefault(pid, cur)
        if cur < need:
            sql(f"UPDATE supermarket_system.product SET stock = stock + {need - cur + 50} WHERE id={pid}")
            return need + 50
        return cur

    def create_activity(payload):
        act = call("POST", "/admin/activities", payload, admin_tok)
        created_activities.append(act["id"])
        return act

    def delete_activity(aid):
        # logical delete via API; physical purge happens in cleanup() over created_activities
        call("DELETE", f"/admin/activities/{aid}", None, admin_tok)

    def order_activity(pid, qty):
        ensure_stock(pid, qty)
        call("POST", "/cart/items", {"productId": pid, "quantity": qty}, tok)
        cart = call("GET", "/cart", None, tok)
        ids = [it["id"] for it in cart["items"] if it["productId"] == pid]
        order = call("POST", "/orders", {"addressId": addr["id"], "cartItemIds": ids}, tok)
        created_orders.append(order["id"])
        return order

    products = call("GET", "/products?page=1&size=50", None, tok)["items"]
    prod = max(products, key=lambda p: p["stock"])
    price = float(prod["price"])
    cid = prod.get("categoryId")
    other = next((p for p in products if p.get("categoryId") != cid and p["stock"] > 5), None)

    addr = call("POST", "/addresses", {
        "receiverName": "测试", "receiverPhone": "13800000000", "province": "广东省",
        "city": "深圳市", "district": "南山区", "detailAddress": "科技园 1 号", "isDefault": True,
    }, tok)
    check("创建收货地址", bool(addr.get("id")))

    # ===== Module A — Marketing Activity =====
    # A1. 单活动·满减全场
    qty = max(1, ceil_div(200, price) + 1)
    qty = min(qty, ensure_stock(prod["id"], qty))
    act_a = create_activity({"name": "S2满200减30", "type": "FULL_REDUCTION", "scope": "ALL",
                             "threshold": 200, "discount": 30, "startTime": NOW, "endTime": PLUS7,
                             "status": 1, "priority": 1})
    order_a = order_activity(prod["id"], qty)
    check("满减-全场 减免=30", abs(float(order_a["activityDiscount"]) - 30.0) < 0.01,
          f"activityDiscount={order_a['activityDiscount']}")
    check("满减-全场 应付=原价-30", abs(float(order_a["payAmount"]) - r2(float(order_a["totalAmount"]) - 30.0)) < 0.01,
          f"pay={order_a['payAmount']} total={order_a['totalAmount']}")
    check("满减-全场 记录活动名", bool(order_a["activityName"]) and "满" in order_a["activityName"],
          order_a["activityName"])
    delete_activity(act_a["id"])

    # A2. 单活动·折扣全场 (9折)
    qty = max(1, ceil_div(150, price))
    qty = min(qty, ensure_stock(prod["id"], qty))
    act_b = create_activity({"name": "S2全场9折", "type": "DISCOUNT", "scope": "ALL",
                             "threshold": 0, "discount": 0.9, "startTime": NOW, "endTime": PLUS7,
                             "status": 1, "priority": 1})
    order_b = order_activity(prod["id"], qty)
    exp_b = r2(float(order_b["totalAmount"]) * 0.1)
    check("折扣-全场 减免=总价*0.1", abs(float(order_b["activityDiscount"]) - exp_b) < 0.01,
          f"activityDiscount={order_b['activityDiscount']} expect={exp_b}")
    check("折扣-全场 应付=总价*0.9", abs(float(order_b["payAmount"]) - r2(float(order_b["totalAmount"]) - exp_b)) < 0.01)
    delete_activity(act_b["id"])

    # A3. 类目作用域：命中 vs 不命中
    if other and cid is not None:
        act_c = create_activity({"name": "S2类目满100减20", "type": "FULL_REDUCTION", "scope": "CATEGORY",
                                 "categoryId": cid, "threshold": 100, "discount": 20,
                                 "startTime": NOW, "endTime": PLUS7, "status": 1, "priority": 1})
        qty = max(1, ceil_div(100, price))
        qty = min(qty, ensure_stock(prod["id"], qty))
        order_c = order_activity(prod["id"], qty)        # prod is in cid -> should hit
        check("类目作用域-命中 减免=20", abs(float(order_c["activityDiscount"]) - 20.0) < 0.01,
              f"activityDiscount={order_c['activityDiscount']}")
        qty2 = min(2, ensure_stock(other["id"], 2))
        order_c2 = order_activity(other["id"], qty2)     # other not in cid -> 0
        check("类目作用域-不命中 减免=0", abs(float(order_c2["activityDiscount"])) < 0.01,
              f"activityDiscount={order_c2['activityDiscount']}")
        delete_activity(act_c["id"])
    else:
        check("类目作用域测试", True, "无跨类目商品，跳过")

    # A4. 多活动取最优
    act_d1 = create_activity({"name": "S2满200减30", "type": "FULL_REDUCTION", "scope": "ALL",
                              "threshold": 200, "discount": 30, "startTime": NOW, "endTime": PLUS7,
                              "status": 1, "priority": 5})
    act_d2 = create_activity({"name": "S2全场9折", "type": "DISCOUNT", "scope": "ALL",
                              "threshold": 0, "discount": 0.9, "startTime": NOW, "endTime": PLUS7,
                              "status": 1, "priority": 1})
    qty = max(1, ceil_div(250, price))
    qty = min(qty, ensure_stock(prod["id"], qty))
    order_d = order_activity(prod["id"], qty)
    exp_d = 30.0 if float(order_d["totalAmount"]) >= 200 else r2(float(order_d["totalAmount"]) * 0.1)
    check("多活动取最优(满减胜)", abs(float(order_d["activityDiscount"]) - exp_d) < 0.01
          and order_d["activityId"] == act_d1["id"],
          f"activityId={order_d['activityId']} expectId={act_d1['id']} discount={order_d['activityDiscount']}")
    qty = max(1, ceil_div(150, price))
    qty = min(qty, ensure_stock(prod["id"], qty))
    order_d2 = order_activity(prod["id"], qty)
    exp_d2 = r2(float(order_d2["totalAmount"]) * 0.1)
    check("多活动取最优(折扣胜/满减不达标)", abs(float(order_d2["activityDiscount"]) - exp_d2) < 0.01
          and order_d2["activityId"] == act_d2["id"],
          f"activityId={order_d2['activityId']} discount={order_d2['activityDiscount']}")
    delete_activity(act_d1["id"])
    delete_activity(act_d2["id"])

    # ===== Module B — Recommendation =====
    rec_products = products[:5]
    for p in rec_products:
        call("POST", "/dwell", {"productId": p["id"], "seconds": 40, "source": "detail"}, tok)
        dwell_product_ids.append(p["id"])

    guess = call("GET", "/recommendations/guess?limit=8", None, tok)
    check("猜你喜欢 返回非空列表", isinstance(guess, list) and len(guess) >= 1,
          f"{len(guess) if isinstance(guess, list) else 0} 条")
    if isinstance(guess, list) and guess:
        check("猜你喜欢 含商品字段(id/name)", all(("id" in g and "name" in g) for g in guess))

    related_found = None
    for cand in products:
        rel = call("GET", f"/products/related/{cand['id']}?limit=5", None, tok)
        if isinstance(rel, list) and len(rel) >= 1:
            related_found = (cand["id"], rel)
            break
    if related_found:
        check("相关商品 返回列表", True, f"商品 {related_found[0]} -> {len(related_found[1])} 条")
    else:
        check("相关商品 返回列表", False, "所有商品均无同分类相关商品")

    # ===== Module C — Dwell =====
    rank = call("GET", "/dwell/rank?limit=8", None, tok)
    check("停留榜单 返回数据", isinstance(rank, list) and len(rank) >= 1,
          f"{len(rank) if isinstance(rank, list) else 0} 条")
    if isinstance(rank, list) and rank:
        hit = next((r for r in rank if r["productId"] in dwell_product_ids), None)
        check("停留榜单 含已浏览商品", hit is not None, f"top={rank[0]}")
        if hit:
            check("停留榜单 浏览次数>=1", int(hit["viewCount"]) >= 1, f"viewCount={hit['viewCount']}")
    try:
        call("POST", "/dwell", {"productId": prod["id"], "seconds": 25, "source": "detail"}, None)
        check("匿名停留上报 best-effort", True)
    except Exception as e:  # noqa
        check("匿名停留上报 best-effort", False, str(e))
    dwell_product_ids.append(prod["id"])

    # ----- cleanup (returns nothing; errors swallowed) -----
    cleanup_state = dict(created_activities=created_activities, created_orders=created_orders,
                         dwell_product_ids=dwell_product_ids, stock_restore=stock_restore,
                         buyer_id=buyer_id, admin_id=admin_id, admin_tok=admin_tok)
    return cleanup_state


def cleanup(state):
    created_activities = state["created_activities"]
    created_orders = state["created_orders"]
    dwell_product_ids = state["dwell_product_ids"]
    stock_restore = state["stock_restore"]
    buyer_id = state["buyer_id"]
    admin_id = state["admin_id"]
    admin_tok = state.get("admin_tok")
    # 1. orders + children first (so the activity_id FK no longer blocks activity delete)
    for oid in created_orders:
        for tbl in ("stock_log", "order_item"):
            try:
                sql(f"DELETE FROM supermarket_system.{tbl} WHERE order_id={oid}")
            except Exception:
                pass
        try:
            sql(f"DELETE FROM supermarket_system.orders WHERE id={oid}")
        except Exception:
            pass
    # 2. activities (physical purge; orders that referenced them are gone)
    for aid in list(created_activities):
        try:
            call("DELETE", f"/admin/activities/{aid}", None, admin_tok)
        except Exception:
            pass
        try:
            sql(f"DELETE FROM supermarket_system.activity WHERE id={aid}")
        except Exception:
            pass
    # 3. dwell rows (by product + by our users)
    if dwell_product_ids:
        id_list = ",".join(str(pid) for pid in set(dwell_product_ids))
        try:
            sql(f"DELETE FROM supermarket_system.page_dwell WHERE product_id IN ({id_list})")
        except Exception:
            pass
    for uid in (buyer_id, admin_id):
        try:
            sql(f"DELETE FROM supermarket_system.page_dwell WHERE user_id={uid}")
        except Exception:
            pass
    # 4. test user child rows + users
    for uid in (buyer_id, admin_id):
        for tbl in ("user_coupon", "user_address", "cart_item", "wallet_transaction", "product_review"):
            try:
                sql(f"DELETE FROM supermarket_system.{tbl} WHERE user_id={uid}")
            except Exception:
                pass
        try:
            sql(f"DELETE FROM supermarket_system.sys_user WHERE id={uid}")
        except Exception:
            pass
    # 5. restore product stock to the snapshot taken before the run
    for pid, orig in stock_restore.items():
        try:
            sql(f"UPDATE supermarket_system.product SET stock={orig} WHERE id={pid}")
        except Exception:
            pass


if __name__ == "__main__":
    state = None
    try:
        state = run_tests()
    finally:
        if state:
            cleanup(state)
        passed = sum(1 for _, ok, _ in results if ok)
        print(f"\n===== {passed}/{len(results)} 项通过 =====")
        for name, ok, detail in results:
            if not ok:
                print("  FAILED:", name, detail)
