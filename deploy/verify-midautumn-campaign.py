"""验证：2026 中秋活动投放是否**真的生效**（不只是建了几行数据）。

投放脚本（`place-midautumn-campaign.py`）负责建数据，本脚本负责**证明它真的对用户起作用**，
两者刻意分开：投放脚本幂等（已存在就跳过），所以重跑它只会打印「已存在」，证不了任何性质。

覆盖：
  A. 公开接口透出：轮播首片指向月饼礼盒 / 中秋公告(PROMOTION) / 秒杀首场是礼盒 /
     热搜含 3 个中秋词 / 会员日含 9-25 / 中秋分类下正好 5 个在售商品
  B. 属性自检（这些最容易被"建了但没配好"骗过去）：
     秒杀价 < 售价、券门槛 > 券额、时间窗口覆盖今天、满减额度 ≤ 门槛、原价 ≥ 售价 ≥ 会员价、图片真的能访问（且是图片）
  C. ⭐ 钱路：加购月饼礼盒（128 ≥ 99）→ `/cart` 的 activityDiscount **必须正好 15** 且活动名对得上；
     单独加蜜柚（19.9 < 99）→ 0（门槛真的生效）；单独加矿泉水（非中秋）→ 0（作用域真的没漏）；
     真下单 → 订单 activityDiscount=15，并按项目金额口径核对 pay
  D. 自清：删买家/订单/券，断言零残留；**不动投放的运营数据**

用法：`python deploy/verify-midautumn-campaign.py`（后端在 8080 跑着）
"""
import json
import time
import urllib.error
import urllib.request

from _db import run_sql

BASE = "http://localhost:8080/api"
STAMP = str(int(time.time()))
BUYER = "vma_" + STAMP
PW = "VmaPass12345"

GIFT = "中秋团圆月饼礼盒 8枚装"
POMELO = "福建平和红心蜜柚 2个装"
WATER = "矿泉水"
ACTIVITY_NAME = "中秋团圆专区 满99减15"
BANNER_URL_MARK = "/api/uploads/banner/"
MEMBER_DAY = "2026-09-25"

results = []
uid = None
created_orders = []


def check(name, ok, detail=""):
    results.append((name, ok))
    print(("PASS " if ok else "FAIL ") + name + (" :: " + str(detail) if detail != "" else ""))


def req(method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    r = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(r, timeout=25) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}


def data_of(resp):
    return resp.get("data") if isinstance(resp, dict) else None


def cleanup():
    if uid is None:
        return
    try:
        run_sql(f"UPDATE stock_log SET operator_id=NULL WHERE operator_id={uid}")
        for t in ("product_review", "wallet_transaction", "user_message", "point_ledger",
                  "user_favorite", "price_alert", "cart_item", "user_address", "user_coupon"):
            run_sql(f"DELETE FROM {t} WHERE user_id={uid}")
        run_sql(f"UPDATE orders SET user_coupon_id=NULL WHERE user_id={uid}")
        for t in ("order_item", "payment_record"):
            run_sql(f"DELETE FROM {t} WHERE order_id IN (SELECT id FROM orders WHERE user_id={uid})")
        run_sql(f"DELETE FROM stock_log WHERE order_id IN (SELECT id FROM orders WHERE user_id={uid})")
        run_sql(f"DELETE FROM orders WHERE user_id={uid}")
        run_sql(f"DELETE FROM sys_user WHERE id={uid}")
    except Exception as e:
        print(f"[cleanup] ⚠️ 清理失败：{e}")


print(f"=== 中秋活动验证（{STAMP}）===\n")

# ---------------------------------------------------------------- A. 公开接口
st, body = req("GET", "/banners")
banners = data_of(body) or []
first = banners[0] if banners else {}
check("A1 轮播首片是中秋主视觉（新上传的 banner 图）",
      st == 200 and BANNER_URL_MARK in str(first.get("imageUrl")), f"首片={first.get('imageUrl')}")
check("A2 轮播首片指向月饼礼盒", bool(first.get("linkProductId")), f"linkProductId={first.get('linkProductId')}")
gift_id = first.get("linkProductId")

st, body = req("GET", "/announcements")
anns = [a for a in (data_of(body) or []) if "中秋" in str(a.get("title"))]
check("A3 中秋公告在位且是 PROMOTION", bool(anns) and anns[0].get("type") == "PROMOTION",
      f"{[(a.get('title'), a.get('type')) for a in anns]}")
check("A4 中秋公告排在最前（用户一眼能看到）",
      bool(data_of(body)) and "中秋" in str((data_of(body) or [{}])[0].get("title")),
      f"首条={(data_of(body) or [{}])[0].get('title')}")

st, body = req("GET", "/flash-sales")
flash = data_of(body) or []
check("A5 秒杀首场是中秋月饼礼盒", bool(flash) and "月饼" in str(flash[0].get("name")),
      f"首场={flash[0].get('name') if flash else None}")

st, body = req("GET", "/hot-searches")
labels = [h.get("label") for h in (data_of(body) or [])]
check("A6 热搜含 3 个中秋词且在最前",
      all(w in labels for w in ("中秋月饼", "平和蜜柚", "阳澄湖大闸蟹")) and labels[:3] == ["中秋月饼", "平和蜜柚", "阳澄湖大闸蟹"],
      f"{labels}")

st, body = req("GET", "/member-days")
view = data_of(body) or {}
days = [d.get("memberDate") for d in (view.get("days") or [])]
check("A7 会员日含中秋当天 9-25 且未过期", MEMBER_DAY in days,
      f"nextDate={view.get('nextDate')} days={days}")
check("A8 会员日倍率是 2（双倍）", float(view.get("multiplier") or 0) == 2.0, f"multiplier={view.get('multiplier')}")

st, body = req("GET", "/categories")
cats = [c for c in (data_of(body) or []) if c.get("name") == "中秋团圆"]
if not cats:
    check("A9 中秋分类存在", False, f"分类={[c.get('name') for c in (data_of(body) or [])]}")
    raise SystemExit(1)
category_id = cats[0]["id"]
st, body = req("GET", f"/products?categoryId={category_id}&page=1&size=50")
items = (data_of(body) or {}).get("items") or []
check("A9 中秋分类下正好 5 个在售商品", st == 200 and len(items) == 5,
      f"分类 id={category_id}，{len(items)} 件：{[i.get('name') for i in items]}")

products = {p["name"]: p for p in items}
by_id = {p["id"]: p for p in items}

# ---------------------------------------------------------------- B. 属性自检
if flash:
    fp = float(flash[0].get("flashPrice") or 0)
    origin = float((by_id.get(flash[0].get("productId")) or {}).get("price") or 0)
    check("B1 秒杀价低于售价（否则违反秒杀规则）", fp > 0 and origin > 0 and fp < origin,
          f"秒杀 {fp} < 售价 {origin}")

st, body = req("GET", "/activities/active")
acts = [a for a in (data_of(body) or []) if a.get("name") == ACTIVITY_NAME]
if acts:
    a = acts[0]
    check("B4 中秋活动：限分类 + 满减额度 ≤ 门槛",
          a.get("scope") == "CATEGORY" and float(a["discount"]) <= float(a["threshold"]),
          f"scope={a.get('scope')} 满{a['threshold']}减{a['discount']}")
    check("B5 中秋活动窗口覆盖今天（含中秋 9-25）",
          str(a["startTime"])[:10] <= "2026-09-23" and str(a["endTime"])[:10] >= "2026-09-27",
          f"{a['startTime']} ~ {a['endTime']}")
else:
    check("B4 中秋活动在进行中", False, f"进行中活动={[x.get('name') for x in (data_of(body) or [])]}")

bad_price = [n for n, p in products.items()
             if not (float(p.get("originalPrice") or 0) >= float(p.get("price") or 0) >= float(p.get("memberPrice") or 0) > 0)]
check("B6 五个商品价格链正常（原价 ≥ 售价 ≥ 会员价 > 0）", not bad_price, f"异常：{bad_price}")
check("B7 五个商品都有库存且带图", all(int(p.get("stock") or 0) > 0 and p.get("coverUrl") for p in products.values()))

urls = [p.get("coverUrl") for p in products.values()] + [first.get("imageUrl")]
bad_img = []
for u in urls:
    try:
        r = urllib.request.urlopen("http://localhost:8080" + u, timeout=15)
        if r.status != 200 or "image" not in (r.headers.get("Content-Type") or ""):
            bad_img.append((u, r.status, r.headers.get("Content-Type")))
    except Exception as e:
        bad_img.append((u, "ERR", str(e)[:40]))
check("B8 六张图都能访问且 Content-Type 是图片（别只看 200）", not bad_img, f"异常：{bad_img}")

# ---------------------------------------------------------------- C. 钱路
phone = "139" + STAMP[-8:]
st, body = req("POST", "/auth/register",
               body={"username": BUYER, "password": PW, "phone": phone, "nickname": "中秋验证"})
assert st == 200 and body.get("code") == 0, f"register failed: {st} {body}"
tok = data_of(body)["token"]
uid = int(run_sql(f"SELECT id FROM sys_user WHERE username='{BUYER}'").split()[0])
req("POST", "/wallet/recharges", tok, {"amount": 1000})

# ⚠️ 券必须在**登录后**查：`/coupons/available` 要登录态，未登录是 401 → 列表为空，
#    会被误判成「券没建」。第一版就是这么假失败的。
st, body = req("GET", "/coupons/available", tok)
coupons = [c for c in (data_of(body) or []) if "中秋" in str(c.get("name"))]
if coupons:
    c = min(coupons, key=lambda x: float(x.get("thresholdAmount")))
    check("B2 ⭐ 中秋券对用户可领：门槛 > 券额", float(c["thresholdAmount"]) > float(c["discountAmount"]),
          f"{c['name']} 满{c['thresholdAmount']}减{c['discountAmount']}")
    check("B3 中秋券窗口覆盖今天（9-23 ~ 9-27）",
          str(c["startTime"])[:10] <= "2026-09-23" and str(c["endTime"])[:10] >= "2026-09-27",
          f"{c['startTime']} ~ {c['endTime']}")
    st, body = req("POST", f"/coupons/{c['id']}/receive", tok)
    check("B9 ⭐ 中秋券能真的领到手", st == 200 and (data_of(body) or {}).get("status") == "UNUSED",
          f"HTTP {st} status={(data_of(body) or {}).get('status')} {(body or {}).get('message', '')}")
else:
    check("B2 ⭐ 中秋券对用户可领", False, f"可领券={[x.get('name') for x in (data_of(body) or [])]}")


def cart_state():
    st, body = req("GET", "/cart", tok)
    d = data_of(body) or {}
    return d


def clear_cart():
    for item in cart_state().get("items") or []:
        req("DELETE", f"/cart/items/{item['id']}", tok)


def add_and_read(name, qty=1):
    clear_cart()
    pid = products[name]["id"] if name in products else None
    if pid is None:
        # 非中秋商品：从全量列表拿
        st, body = req("GET", "/products?page=1&size=100", tok)
        pid = next(p["id"] for p in (data_of(body) or {}).get("items", []) if p["name"] == name)
    req("POST", "/cart/items", tok, {"productId": pid, "quantity": qty})
    return cart_state()


gift_cart = add_and_read(GIFT)
check("C1 ⭐ 加购月饼礼盒 → 命中中秋活动，正好减 15",
      float(gift_cart.get("activityDiscount") or 0) == 15 and gift_cart.get("activityName") == ACTIVITY_NAME,
      f"activityDiscount={gift_cart.get('activityDiscount')} activityName={gift_cart.get('activityName')}")

low_cart = add_and_read(POMELO)
check("C2 只加蜜柚（19.9 < 99）→ 不减免（门槛真的生效）",
      float(low_cart.get("activityDiscount") or 0) == 0,
      f"selectedAmount={low_cart.get('selectedAmount')} activityDiscount={low_cart.get('activityDiscount')}")

other_cart = add_and_read(WATER)
check("C3 只加矿泉水（非中秋分类）→ 不减免（作用域没漏到全站）",
      float(other_cart.get("activityDiscount") or 0) == 0,
      f"activityDiscount={other_cart.get('activityDiscount')}")

# 真下单（用 C1 的购物车状态重建）
gift_cart = add_and_read(GIFT)
address = data_of(req("POST", "/addresses", tok, {
    "receiverName": "李四", "receiverPhone": "13800000001", "province": "广东省",
    "city": "深圳市", "district": "南山区", "detailAddress": "测试路 2 号", "isDefault": True,
})[1])
st, body = req("POST", "/orders", tok, {
    "addressId": address["id"],
    "cartItemIds": [item["id"] for item in gift_cart["items"]],
    "remark": "中秋活动验证",
})
order = data_of(body) or {}
created_orders.append(order.get("id"))
check("C4 下单成功", st == 200 and order.get("id"), f"HTTP {st} {(body or {}).get('message', '')}")
check("C5 ⭐ 订单上的活动减免 = 15（与购物车一致）",
      float(order.get("activityDiscount") or 0) == 15, f"activityDiscount={order.get('activityDiscount')}")

# 金额口径（照 FEATURES.md 的契约：每一种优惠都有独立金额列）
expected = (float(order.get("totalAmount") or 0) + float(order.get("freightAmount") or 0)
            - float(order.get("discountAmount") or 0) - float(order.get("activityDiscount") or 0)
            - float(order.get("memberDiscount") or 0) - float(order.get("pointsDiscount") or 0))
check("C6 应付 = 小计 + 运费 − 券 − 活动 − 会员 − 积分",
      abs(round(expected, 2) - float(order.get("payAmount") or 0)) < 0.01,
      f"算得 {round(expected, 2)} 实付 {order.get('payAmount')}")

# ---------------------------------------------------------------- D. 清理
cleanup()
left = run_sql(f"SELECT COUNT(*) FROM sys_user WHERE username LIKE 'vma%'").strip()
check("D1 测试账号已清理", left == "0", f"残留={left}")

# 投放的运营数据必须**原样还在**（验证脚本不该动它们）
kept = run_sql(f"SELECT COUNT(*) FROM product WHERE category_id={category_id} AND deleted=0").strip()
check("D2 中秋的 5 个商品未被验证脚本动过", kept == "5", f"count={kept}")
kept = run_sql(f"SELECT COUNT(*) FROM activity WHERE name='{ACTIVITY_NAME}' AND deleted=0").strip()
check("D3 中秋活动仍在", kept == "1", f"count={kept}")
kept = run_sql(f"SELECT COUNT(*) FROM member_day WHERE member_date='{MEMBER_DAY}' AND enabled=1").strip()
check("D4 会员日 9-25 仍在启用", kept == "1", f"count={kept}")

passed = sum(1 for _, ok in results if ok)
print(f"\n==== {passed}/{len(results)} 项通过 ====")
print("CLEANUP_OK" if left == "0" else "CLEANUP_FAILED")
raise SystemExit(0 if passed == len(results) else 1)
