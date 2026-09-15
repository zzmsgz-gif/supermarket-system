"""秒杀「每人限购」三道防线验证：接口透出额度 → 加购/改数量即拦 → 下单原子扣减兜底。

起因：原先限购只在「下单」那一步校验，用户能在购物车里把数量加到远超上限，
一路填完地址点下结算才被打回 —— 白费操作，而且不知道错在哪。

覆盖：
  A. 额度透出：游客不返回、登录后返回「我的已购 / 未付款占用 / 还能买几件」、不限购场次为 null
  B. 加购即拦：达到上限前放行、超出即 409；被拒后购物车数量不被改动
  C. 改数量即拦：等于上限放行 / 超出 409 / **减少数量必须放行**（关键回归，否则用户被锁死）
  D. 用户隔离：每人限购按用户各算各的
  E. 未付款订单占名额：计数正确 + 给出订单号 + 取消后额度与名额同时恢复
  F. 支付后仍占用：限购额度不会被"付掉"洗白，且不再计为「未付款占用」
  G. 边界与设计决定：库存上限未回归、场次停用后不再受限、
     「只拦每人限购、不拦全站剩余名额」被显式验证（购物车放行、下单兜底）
  H. 对账：接口值 == 手工 SQL；订单金额恒等式未被破坏

自建测试账号/场次，finally 清库（先断 orders↔user_coupon 环、再删流水、最后删订单与用户）。
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

buyer1 = "fll1_" + STAMP
buyer2 = "fll2_" + STAMP
admin = "flladm_" + STAMP
PW = "Fll12345"

PID = 79          # 大米（开秒杀、限购 2）
CTRL_PID = 74     # 抽纸（开秒杀、不限购）

uid1 = uid2 = admin_id = None
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


def err_message(payload):
    if isinstance(payload, dict):
        return str(payload.get("message") or payload.get("msg") or payload)
    return str(payload)


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


try:
    # ============ 准备：管理员 + 两个买家 ============
    admin_tok = call("POST", "/auth/register",
                     {"username": admin, "password": PW, "nickname": "限购管理员",
                      "phone": "137" + STAMP[-8:]})["token"]
    admin_id = call("GET", "/auth/me", None, admin_tok)["id"]
    sql(f"UPDATE {DB}.sys_user SET role='ADMIN' WHERE id={admin_id}")
    admin_tok = call("POST", "/auth/login", {"username": admin, "password": PW})["token"]

    tok1 = call("POST", "/auth/register",
                {"username": buyer1, "password": PW, "nickname": "限购买家一",
                 "phone": "138" + STAMP[-8:]})["token"]
    uid1 = call("GET", "/auth/me", None, tok1)["id"]
    call("POST", "/wallet/recharges", {"amount": 1000}, tok1)
    address1 = call("POST", "/addresses", {"receiverName": "限购收一", "receiverPhone": "138" + STAMP[-8:],
                                          "province": "广东省", "city": "深圳市", "district": "南山区",
                                          "detailAddress": "限购路 1 号", "isDefault": True}, tok1)
    address1 = call("GET", "/addresses", None, tok1)[0]["id"]

    tok2 = call("POST", "/auth/register",
                {"username": buyer2, "password": PW, "nickname": "限购买家二",
                 "phone": "139" + STAMP[-8:]})["token"]
    uid2 = call("GET", "/auth/me", None, tok2)["id"]
    call("POST", "/wallet/recharges", {"amount": 1000}, tok2)
    call("POST", "/addresses", {"receiverName": "限购收二", "receiverPhone": "139" + STAMP[-8:],
                                "province": "广东省", "city": "深圳市", "district": "南山区",
                                "detailAddress": "限购路 2 号", "isDefault": True}, tok2)
    address2 = call("GET", "/addresses", None, tok2)[0]["id"]

    base_price = float(sql(f"SELECT price FROM {DB}.product WHERE id={PID}"))
    ctrl_price = float(sql(f"SELECT price FROM {DB}.product WHERE id={CTRL_PID}"))

    sale1 = call("POST", "/admin/flash-sales", {
        "productId": PID, "name": "限购验证_" + STAMP, "flashPrice": round(base_price * 0.5, 2),
        "totalQuota": 20, "perUserLimit": 2, "startTime": ts(-1), "endTime": ts(24),
        "status": 1, "sortNo": 9}, admin_tok)
    created_sales.append(sale1["id"])

    sale2 = call("POST", "/admin/flash-sales", {
        "productId": CTRL_PID, "name": "不限购验证_" + STAMP, "flashPrice": round(ctrl_price * 0.5, 2),
        "totalQuota": 20, "perUserLimit": 0, "startTime": ts(-1), "endTime": ts(24),
        "status": 1, "sortNo": 9}, admin_tok)
    created_sales.append(sale2["id"])

    # ============ A. 额度透出 ============
    guest = call("GET", "/flash-sales")
    g = next((s for s in guest if s["id"] == sale1["id"]), None)
    check("A1 游客不返回「我的额度」（没有身份就不做无依据的限制）",
          g is not None and g.get("myBoughtQuantity") is None and g.get("myRemainingQuota") is None,
          f"myBoughtQuantity={g.get('myBoughtQuantity') if g else '-'}")

    mine = sale_of(tok1, sale1["id"])
    check("A2 登录后透出「已购 0 / 还能买 2 / 未付款 0」",
          mine.get("myBoughtQuantity") == 0 and mine.get("myRemainingQuota") == 2
          and mine.get("myUnpaidQuantity") == 0,
          f"bought={mine.get('myBoughtQuantity')} left={mine.get('myRemainingQuota')} "
          f"unpaid={mine.get('myUnpaidQuantity')}")

    nolimit = sale_of(tok1, sale2["id"])
    check("A3 不限购场次的「还能买几件」为 null（表示无上限，前端据此不做限制）",
          nolimit.get("myRemainingQuota") is None and nolimit.get("myBoughtQuantity") == 0,
          f"left={nolimit.get('myRemainingQuota')}")

    # ============ B. 加购即拦（本次修复的核心） ============
    st = status_of("POST", "/cart/items", {"productId": PID, "quantity": 1}, tok1)
    line = cart_line(tok1, PID)
    check("B1 加购 1 件（限购 2）→ 放行", st < 400 and line and line["quantity"] == 1,
          f"HTTP {st} qty={line['quantity'] if line else '-'}")

    st = status_of("POST", "/cart/items", {"productId": PID, "quantity": 1}, tok1)
    line = cart_line(tok1, PID)
    check("B2 加购到 2 件（正好等于上限）→ 放行", st < 400 and line["quantity"] == 2,
          f"HTTP {st} qty={line['quantity']}")

    st, payload = request("POST", "/cart/items", {"productId": PID, "quantity": 1}, tok1)
    msg = err_message(payload)
    check("B3 ⭐ 再加 1 件（超出上限）→ 409，且文案说清上限卡在哪",
          st == 409 and "限购" in msg and "最多放" in msg, f"HTTP {st} | {msg}")

    check("B3b 文案不自相矛盾：不说「你还能买 N 件」（此时购物车里就已经放着 2 件）",
          "还能买" not in msg, msg)

    line = cart_line(tok1, PID)
    check("B4 被拒后购物车数量未被改动（仍是 2，没留半截状态）", line["quantity"] == 2,
          f"qty={line['quantity']}")

    st = status_of("POST", "/cart/items", {"productId": CTRL_PID, "quantity": 5}, tok1)
    line = cart_line(tok1, CTRL_PID)
    check("B5 不限购场次加购 5 件 → 放行（限购=0 视为不限）", st < 400 and line["quantity"] == 5,
          f"HTTP {st} qty={line['quantity'] if line else '-'}")

    # ============ C. 改数量即拦 ============
    item_id = line_id = cart_line(tok1, PID)["id"]

    st, p = request("PUT", f"/cart/items/{item_id}", {"quantity": 2}, tok1)
    check("C1 改数量为上限 2 → 放行", st < 400, f"HTTP {st}")

    st, p = request("PUT", f"/cart/items/{item_id}", {"quantity": 3}, tok1)
    msg = err_message(p)
    check("C2 改数量为 3（超上限）→ 409 且文案说清上限卡在哪",
          st == 409 and "限购" in msg and "最多放" in msg, f"HTTP {st} | {msg}")

    st, p = request("PUT", f"/cart/items/{item_id}", {"quantity": 1}, tok1)
    check("C3 ⭐ 改数量为 1（减少）→ 必须放行", st < 400, f"HTTP {st} | {err_message(p)}")

    # 直接改库把购物车顶到超限，再验证「减少」依然放行 —— 这是"用户被锁死"的核心场景：
    # 后台把限购调小、或名额被别人抢走时，用户至少还能把数量改小、能删掉
    sql(f"UPDATE {DB}.cart_item SET quantity=3 WHERE id={item_id}")
    st, p = request("PUT", f"/cart/items/{item_id}", {"quantity": 2}, tok1)
    check("C4 ⭐ 购物车已超限时把数量改小 → 必须放行（否则用户被锁在收拾不了的购物车里）",
          st < 400, f"HTTP {st} | {err_message(p)}")
    sql(f"UPDATE {DB}.cart_item SET quantity=2 WHERE id={item_id}")

    # 用例要落在「超过库存、但能通过 @Max(999) 校验」的区间，否则会被校验层拦成 400，
    # 测不到库存这道防线（第一版写成 99999 就踩了这个）
    stock = int(sql(f"SELECT stock FROM {DB}.product WHERE id={PID}"))
    st = status_of("PUT", f"/cart/items/{item_id}", {"quantity": min(stock + 1, 999)}, tok1)
    check("C5 库存上限未回归：数量超过库存 → 409", st == 409, f"HTTP {st}（库存 {stock}）")

    # ============ D. 用户隔离 ============
    call("POST", "/cart/items", {"productId": PID, "quantity": 2}, tok2)
    line2 = cart_line(tok2, PID)
    check("D1 另一用户不受影响（每人限购按用户各算各的）", line2 and line2["quantity"] == 2,
          f"qty={line2['quantity'] if line2 else '-'}")

    mine2 = sale_of(tok2, sale1["id"])
    check("D2 另一用户的「我的已购」独立计数",
          mine2.get("myBoughtQuantity") == 0 and mine2.get("myRemainingQuota") == 2,
          f"bought={mine2.get('myBoughtQuantity')} left={mine2.get('myRemainingQuota')}")

    # ============ E. 未付款订单占名额 ============
    order1 = place_order(tok1, address1, PID)
    check("E1 下单成功且为未付款", order1["status"] == "PENDING_PAYMENT", order1["status"])

    mine = sale_of(tok1, sale1["id"])
    check("E2 ⭐ 未付款订单也占名额：已购 2 / 未付款 2 / 还能买 0",
          mine.get("myBoughtQuantity") == 2 and mine.get("myUnpaidQuantity") == 2
          and mine.get("myRemainingQuota") == 0,
          f"bought={mine.get('myBoughtQuantity')} unpaid={mine.get('myUnpaidQuantity')} "
          f"left={mine.get('myRemainingQuota')}")

    check("E3 返回未付款订单号与 id，供前台给出「去支付 / 取消订单」入口",
          mine.get("myUnpaidOrderNo") == order1["orderNo"] and mine.get("myUnpaidOrderId") == order1["id"],
          f"orderNo={mine.get('myUnpaidOrderNo')} id={mine.get('myUnpaidOrderId')}")

    st, p = request("POST", "/cart/items", {"productId": PID, "quantity": 1}, tok1)
    msg = err_message(p)
    check("E4 已买满时再加购 → 409 且文案含「买满」", st == 409 and "买满" in msg,
          f"HTTP {st} | {msg}")

    sold_before = int(sql(f"SELECT sold_quota FROM {DB}.flash_sale WHERE id={sale1['id']}"))
    call("POST", f"/orders/{order1['id']}/cancel", None, tok1)
    sold_after = int(sql(f"SELECT sold_quota FROM {DB}.flash_sale WHERE id={sale1['id']}"))
    check("E5 取消订单后秒杀名额回退 2 件", sold_before - sold_after == 2,
          f"{sold_before} → {sold_after}")

    mine = sale_of(tok1, sale1["id"])
    check("E6 取消后额度恢复：已购 0 / 还能买 2 / 未付款 0",
          mine.get("myBoughtQuantity") == 0 and mine.get("myRemainingQuota") == 2
          and mine.get("myUnpaidQuantity") == 0,
          f"bought={mine.get('myBoughtQuantity')} left={mine.get('myRemainingQuota')} "
          f"unpaid={mine.get('myUnpaidQuantity')}")

    st = status_of("POST", "/cart/items", {"productId": PID, "quantity": 2}, tok1)
    line = cart_line(tok1, PID)
    check("E7 取消后又能加购了（额度实时恢复，无需刷新页面重登）",
          st < 400 and line["quantity"] == 2, f"HTTP {st} qty={line['quantity'] if line else '-'}")

    # ============ F. 支付后仍占用 ============
    order2 = place_order(tok1, address1, PID)
    call("POST", f"/orders/{order2['id']}/pay", None, tok1)
    paid = call("GET", f"/orders/{order2['id']}", None, tok1)
    check("F1 订单已支付", paid["status"] in ("PAID", "SHIPPED", "COMPLETED"), paid["status"])

    mine = sale_of(tok1, sale1["id"])
    check("F2 ⭐ 支付后限购额度不放开（已购仍为 2 / 还能买 0）",
          mine.get("myBoughtQuantity") == 2 and mine.get("myRemainingQuota") == 0,
          f"bought={mine.get('myBoughtQuantity')} left={mine.get('myRemainingQuota')}")

    check("F3 支付后不再计为「未付款占用」（这条区分开了已购与待付款）",
          mine.get("myUnpaidQuantity") == 0 and mine.get("myUnpaidOrderNo") is None,
          f"unpaid={mine.get('myUnpaidQuantity')} orderNo={mine.get('myUnpaidOrderNo')}")

    st = status_of("POST", "/cart/items", {"productId": PID, "quantity": 1}, tok1)
    check("F4 已支付满额后继续加购 → 409", st == 409, f"HTTP {st}")

    # ============ G. 边界与设计决定 ============
    call("PATCH", f"/admin/flash-sales/{sale1['id']}/status", {"status": 0}, admin_tok)
    st = status_of("POST", "/cart/items", {"productId": PID, "quantity": 1}, tok1)
    check("G1 场次停用后加购不再受该场次限购约束（此时它已不是秒杀商品）", st < 400, f"HTTP {st}")
    call("PATCH", f"/admin/flash-sales/{sale1['id']}/status", {"status": 1}, admin_tok)

    # 「只拦每人限购、不拦全站剩余名额」是刻意的设计决定：剩余名额所有用户共享且随时在变，
    # 拿它做硬拦会出现「名额被别人抢走一件，连把自己购物车里的数量改小都被拒」。
    # 所以加购层放行，由下单时的原子扣减兜底 —— 下面两条把这个决定固化下来。
    clear_cart(tok2)
    sold_snapshot = int(sql(f"SELECT sold_quota FROM {DB}.flash_sale WHERE id={sale1['id']}"))
    sql(f"UPDATE {DB}.flash_sale SET sold_quota = total_quota - 1 WHERE id={sale1['id']}")
    st = status_of("POST", "/cart/items", {"productId": PID, "quantity": 2}, tok2)
    check("G2 全站只剩 1 件时，仍可把 2 件放进购物车（加购层不拦剩余名额）", st < 400, f"HTTP {st}")

    cart2 = call("GET", "/cart", None, tok2)
    ids2 = [i["id"] for i in cart2.get("items", []) if i["productId"] == PID]
    st, p = request("POST", "/orders", {"cartItemIds": ids2, "addressId": address2,
                                        "fulfillmentType": "DELIVERY"}, tok2)
    msg = err_message(p)
    check("G3 ⭐ 但下单时被名额拦下 → 409（兜底防线生效，不会超卖）",
          st == 409 and ("抢完" in msg or "名额" in msg), f"HTTP {st} | {msg}")
    sql(f"UPDATE {DB}.flash_sale SET sold_quota = {sold_snapshot} WHERE id={sale1['id']}")

    # ============ I. 「已下单占用」这一分支的文案 ============
    # 与「已买满」是两句不同的话：0 < 已购 < 限购 时要告诉用户剩下的额度为什么比限购少，
    # 否则用户以为限购被凭空扣掉了。用买家二构造：先占 1 件，再往购物车放 2 件。
    clear_cart(tok2)
    call("POST", "/cart/items", {"productId": PID, "quantity": 1}, tok2)
    order3 = place_order(tok2, address2, PID)
    call("POST", "/cart/items", {"productId": PID, "quantity": 1}, tok2)
    line2 = cart_line(tok2, PID)
    sql(f"UPDATE {DB}.cart_item SET quantity=2 WHERE id={line2['id']}")

    mine2 = sale_of(tok2, sale1["id"])
    check("I1 已下单 1 件后额度正确（已购 1 / 还能买 1）",
          mine2.get("myBoughtQuantity") == 1 and mine2.get("myRemainingQuota") == 1,
          f"bought={mine2.get('myBoughtQuantity')} left={mine2.get('myRemainingQuota')}")

    st, p = request("PUT", f"/cart/items/{line2['id']}", {"quantity": 3}, tok2)
    msg = err_message(p)
    check("I2 ⭐ 部分占用时的文案点明「已下单占用 N 件」，而不是只报一个对不上的数字",
          st == 409 and "最多放 1 件" in msg and "已下单占用 1 件" in msg, f"HTTP {st} | {msg}")

    three = call("GET", f"/orders/{order3['id']}", None, tok2)
    check("I3 该未付款订单也被正确计入「未付款占用」",
          sale_of(tok2, sale1["id"]).get("myUnpaidOrderNo") == three["orderNo"],
          f"orderNo={three['orderNo']}")

    # ============ H. 对账 ============
    after = sale_of(tok1, sale1["id"])
    sql_bought = int(sql(
        f"SELECT COALESCE(SUM(i.quantity),0) FROM {DB}.order_item i "
        f"JOIN {DB}.orders o ON o.id=i.order_id "
        f"WHERE i.flash_sale_id={sale1['id']} AND o.user_id={uid1} "
        f"AND o.status NOT IN ('CANCELED','CLOSED')"))
    check("H1 接口的「我的已购」与手工 SQL 对账一致",
          after.get("myBoughtQuantity") == sql_bought,
          f"接口={after.get('myBoughtQuantity')} SQL={sql_bought}")

    bad = int(sql(
        f"SELECT COUNT(*) FROM {DB}.orders WHERE total_amount+freight_amount"
        f"-discount_amount-activity_discount-member_discount-points_discount <> pay_amount"))
    check("H2 订单金额恒等式未被破坏（全库 0 条不一致）", bad == 0, f"{bad} 条")

    leaked = int(sql(
        f"SELECT COUNT(*) FROM {DB}.cart_item WHERE user_id IN "
        f"({uid1},{uid2}) AND quantity > 5"))
    check("H3 限购校验没把数量写坏（无异常大的购物车行）", leaked == 0, f"{leaked} 行")

finally:
    try:
        for sid in created_sales:
            sql(f"DELETE FROM {DB}.flash_sale WHERE id={sid}")
        ids = [i for i in (uid1, uid2, admin_id) if i is not None]
        idlist = ",".join(str(i) for i in ids) or "NULL"
        olist = ",".join(str(i) for i in created_orders) or "NULL"
        # 还原库存：**必须排除已取消/已关闭的订单** —— 它们已经通过取消流程回退过库存了，
        # 这里再回退一次就会让库存凭空多出来。
        if created_orders:
            sql(f"UPDATE {DB}.product p JOIN ("
                f"SELECT oi.product_id AS pid, SUM(oi.quantity) AS q FROM {DB}.order_item oi "
                f"JOIN {DB}.orders o ON o.id = oi.order_id "
                f"WHERE oi.order_id IN ({olist}) AND o.status NOT IN ('CANCELED','CLOSED') "
                f"GROUP BY oi.product_id) t ON t.pid=p.id "
                f"SET p.stock=p.stock+t.q, p.sales=GREATEST(p.sales-t.q,0)")
            sql(f"UPDATE {DB}.orders SET user_coupon_id=NULL WHERE id IN ({olist})")
        for t in ("user_message", "wallet_transaction", "point_ledger", "user_favorite",
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
