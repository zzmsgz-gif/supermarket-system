"""第三档：限时秒杀 + 经营看板 + 协议/隐私正文 端到端验证（后端 API 层）。

覆盖：
  A. 秒杀后台 CRUD 校验：秒杀价必须低于售价 / 起止时间校验 / 同商品场次重叠 409 / 修改与停用删除
  B. 秒杀计价：命中进行中的场次按秒杀价结算，订单行快照 flash_sale_id，商品行单价=秒杀价
  C. 名额：原子扣减、名额不足 409（防超卖）、取消订单回退名额
  D. 每人限购：超出限购 409，且不消耗名额
  E. 不参与计价的情形：已结束/已停用场次不下秒杀价
  F. 经营看板：与手工 SQL 对账（GMV/订单数/客单价/新增用户/复购率/品类占比），区间切换生效
  G. 协议/隐私：公开可读、后台可改、改后可读回、未知 key 404

自建测试账号/门店/场次，finally 清库（先断 orders↔user_coupon 环、再删流水、最后删订单与用户）。
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
TOL = 0.011

buyer = "fsv_" + STAMP
admin = "fsadmin_" + STAMP
PW = "Fsv12345"
buyer_phone = "139" + STAMP[-8:]
admin_phone = "138" + STAMP[-8:]

# 选两个在售商品：一个开秒杀、一个对照
PID = 79          # 大米 39.90
CTRL_PID = 74     # 抽纸 19.90

uid = None
admin_id = None
created_orders = []
created_sales = []
results = []
orig_doc_content = {}


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


def num(obj, key):
    return float(obj.get(key) or 0)


def near(a, b, tol=TOL):
    return abs(float(a) - float(b)) <= tol


def ts(hours_from_now):
    return (datetime.now() + timedelta(hours=hours_from_now)).strftime("%Y-%m-%dT%H:%M")


try:
    # ============ 准备：管理员 + 买家 ============
    admin_tok = call("POST", "/auth/register",
                     {"username": admin, "password": PW, "nickname": "秒杀管理员",
                      "phone": admin_phone})["token"]
    admin_id = call("GET", "/auth/me", None, admin_tok)["id"]
    sql(f"UPDATE {DB}.sys_user SET role='ADMIN' WHERE id={admin_id}")
    admin_tok = call("POST", "/auth/login", {"username": admin, "password": PW})["token"]

    tok = call("POST", "/auth/register",
               {"username": buyer, "password": PW, "nickname": "秒杀买家",
                "phone": buyer_phone})["token"]
    uid = call("GET", "/auth/me", None, tok)["id"]
    call("POST", "/wallet/recharges", {"amount": 2000}, tok)
    call("POST", "/addresses", {"receiverName": "秒杀收", "receiverPhone": buyer_phone,
                                "province": "广东省", "city": "深圳市", "district": "南山区",
                                "detailAddress": "秒杀路 1 号", "isDefault": True}, tok)
    address_id = call("GET", "/addresses", None, tok)[0]["id"]

    price_row = sql(f"SELECT price FROM {DB}.product WHERE id={PID}")
    base_price = float(price_row)
    flash_price = round(base_price * 0.5, 2)

    # ============ A. 后台 CRUD 校验 ============
    bad_price = status_of("POST", "/admin/flash-sales", {
        "productId": PID, "flashPrice": round(base_price + 1, 2), "totalQuota": 10,
        "startTime": ts(-1), "endTime": ts(24)}, admin_tok)
    check("A1 秒杀价不低于售价 → 400", bad_price == 400, f"HTTP {bad_price}")

    bad_time = status_of("POST", "/admin/flash-sales", {
        "productId": PID, "flashPrice": flash_price, "totalQuota": 10,
        "startTime": ts(24), "endTime": ts(1)}, admin_tok)
    check("A2 开始时间晚于结束时间 → 400", bad_time == 400, f"HTTP {bad_time}")

    bad_quota = status_of("POST", "/admin/flash-sales", {
        "productId": PID, "flashPrice": flash_price, "totalQuota": 0,
        "startTime": ts(-1), "endTime": ts(24)}, admin_tok)
    check("A3 秒杀名额为 0 → 400", bad_quota == 400, f"HTTP {bad_quota}")

    sale = call("POST", "/admin/flash-sales", {
        "productId": PID, "name": "验证秒杀_" + STAMP, "flashPrice": flash_price,
        "totalQuota": 3, "perUserLimit": 2, "startTime": ts(-1), "endTime": ts(24),
        "status": 1, "sortNo": 5}, admin_tok)
    created_sales.append(sale["id"])
    check("A4 创建秒杀场次成功", sale["id"] is not None and near(sale["flashPrice"], flash_price),
          f"id={sale['id']} 秒杀价={sale['flashPrice']} 状态={sale['state']}")

    dup = status_of("POST", "/admin/flash-sales", {
        "productId": PID, "flashPrice": flash_price, "totalQuota": 5,
        "startTime": ts(1), "endTime": ts(2)}, admin_tok)
    check("A5 同商品存在未结束场次 → 409", dup == 409, f"HTTP {dup}")

    updated = call("PUT", f"/admin/flash-sales/{sale['id']}", {
        "productId": PID, "name": "验证秒杀_" + STAMP, "flashPrice": flash_price,
        "totalQuota": 3, "perUserLimit": 2, "startTime": ts(-1), "endTime": ts(25),
        "status": 1, "sortNo": 5}, admin_tok)
    check("A6 编辑场次成功", updated["id"] == sale["id"])

    bad_pid = status_of("POST", "/admin/flash-sales", {
        "productId": 99999999, "flashPrice": 1, "totalQuota": 10,
        "startTime": ts(-1), "endTime": ts(24)}, admin_tok)
    check("A7 商品不存在 → 404", bad_pid == 404, f"HTTP {bad_pid}")

    # ============ B/C. 秒杀计价与名额 ============
    public_list = call("GET", "/flash-sales")
    mine = next((s for s in public_list if s["id"] == sale["id"]), None)
    check("B1 秒杀出现在前台列表且状态为进行中", mine is not None and mine["state"] == "RUNNING",
          f"remainingQuota={mine['remainingQuota'] if mine else '-'}")

    call("POST", "/cart/items", {"productId": PID, "quantity": 2}, tok)
    call("POST", "/cart/items", {"productId": CTRL_PID, "quantity": 1}, tok)
    cart = call("GET", "/cart", None, tok)
    flash_line = next(i for i in cart["items"] if i["productId"] == PID)
    ctrl_line = next(i for i in cart["items"] if i["productId"] == CTRL_PID)
    check("B2 购物车按秒杀价计价（与下单同口径）",
          near(num(flash_line, "productPrice"), flash_price) and flash_line.get("flashSaleId") == sale["id"],
          f"购物车单价={num(flash_line, 'productPrice')} 秒杀价={flash_price}")
    check("B3 未开秒杀的商品仍按原价", not ctrl_line.get("flashSaleId"),
          f"{ctrl_line['productName']} 单价={num(ctrl_line, 'productPrice')}")

    items = [i["id"] for i in cart["items"]]
    order = call("POST", "/orders", {"cartItemIds": items, "addressId": address_id,
                                     "fulfillmentType": "DELIVERY", "remark": "秒杀验证"}, tok)
    created_orders.append(order["id"])
    detail = call("GET", f"/orders/{order['id']}", None, tok)
    flash_item = next(i for i in detail["items"] if i["productId"] == PID)
    check("B4 订单行按秒杀价结算并快照 flashSaleId",
          near(num(flash_item, "productPrice"), flash_price) and flash_item.get("flashSaleId") == sale["id"],
          f"单价={num(flash_item, 'productPrice')} flashSaleId={flash_item.get('flashSaleId')}")
    check("B5 订单金额恒等式仍成立",
          near(num(detail, "totalAmount") + num(detail, "freightAmount") - num(detail, "discountAmount")
               - num(detail, "activityDiscount") - num(detail, "memberDiscount")
               - num(detail, "pointsDiscount"), num(detail, "payAmount")),
          f"小计={num(detail, 'totalAmount')} 实付={num(detail, 'payAmount')}")

    sold_after_order = int(sql(f"SELECT sold_quota FROM {DB}.flash_sale WHERE id={sale['id']}"))
    check("C1 下单后名额被扣减（2 件）", sold_after_order == 2, f"sold_quota={sold_after_order}")

    # 名额只剩 1，再买 2 件应 409（防超卖）
    call("POST", "/cart/items", {"productId": PID, "quantity": 2}, tok)
    cart2 = call("GET", "/cart", None, tok)
    ids2 = [i["id"] for i in cart2["items"] if i["productId"] == PID]
    over = status_of("POST", "/orders", {"cartItemIds": ids2, "addressId": address_id,
                                         "fulfillmentType": "DELIVERY"}, tok)
    check("C2 名额不足（剩 1 买 2）→ 409，防超卖", over == 409, f"HTTP {over}")
    sold_after_over = int(sql(f"SELECT sold_quota FROM {DB}.flash_sale WHERE id={sale['id']}"))
    check("C3 失败的下单不留下名额占用", sold_after_over == 2, f"sold_quota={sold_after_over}")

    shrink = status_of("PUT", f"/admin/flash-sales/{sale['id']}", {
        "productId": PID, "name": "验证秒杀_" + STAMP, "flashPrice": flash_price,
        "totalQuota": 1, "perUserLimit": 2, "startTime": ts(-1), "endTime": ts(25)}, admin_tok)
    check("C4 名额改到小于已抢数量（已抢 2 → 改成 1）→ 400", shrink == 400, f"HTTP {shrink}")

    # ============ D. 每人限购 ============
    call("POST", "/cart/items", {"productId": PID, "quantity": 1}, tok)
    cart3 = call("GET", "/cart", None, tok)
    ids3 = [i["id"] for i in cart3["items"] if i["productId"] == PID]
    limit_case = status_of("POST", "/orders", {"cartItemIds": ids3, "addressId": address_id,
                                               "fulfillmentType": "DELIVERY"}, tok)
    check("D1 超过每人限购（已买 2、限购 2）→ 409", limit_case == 409, f"HTTP {limit_case}")

    # ============ E. 取消回退名额 ============
    call("POST", f"/orders/{order['id']}/cancel", None, tok)
    sold_after_cancel = int(sql(f"SELECT sold_quota FROM {DB}.flash_sale WHERE id={sale['id']}"))
    check("E1 取消订单后名额回退到 0", sold_after_cancel == 0, f"sold_quota={sold_after_cancel}")

    call("PATCH", f"/admin/flash-sales/{sale['id']}/status", {"status": 0}, admin_tok)
    idle_price = sql(f"SELECT price FROM {DB}.product WHERE id={PID}")
    call("POST", "/cart/items", {"productId": PID, "quantity": 1}, tok)
    cart4 = call("GET", "/cart", None, tok)
    line4 = next(i for i in cart4["items"] if i["productId"] == PID)
    check("E2 场次停用后不再按秒杀价计价",
          line4.get("flashSaleId") is None and near(num(line4, "productPrice"), float(idle_price)),
          f"单价={num(line4, 'productPrice')} 原价={idle_price}")
    call("PATCH", f"/admin/flash-sales/{sale['id']}/status", {"status": 1}, admin_tok)

    # ============ F. 经营看板 ============
    dash = call("GET", "/admin/stats/dashboard?range=7d", None, admin_tok)
    sql_gmv = float(sql(
        f"SELECT COALESCE(SUM(pay_amount),0) FROM {DB}.orders WHERE status IN ('PAID','SHIPPED','COMPLETED') "
        f"AND created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)"))
    sql_orders = int(sql(
        f"SELECT COUNT(*) FROM {DB}.orders WHERE status IN ('PAID','SHIPPED','COMPLETED') "
        f"AND created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)"))
    check("F1 看板 GMV 与手工 SQL 一致", near(num(dash["trade"], "gmv"), sql_gmv),
          f"看板={num(dash['trade'], 'gmv')} SQL={sql_gmv}")
    check("F2 看板成交订单数与 SQL 一致", dash["trade"]["paidOrderCount"] == sql_orders,
          f"看板={dash['trade']['paidOrderCount']} SQL={sql_orders}")
    expected_aov = round(sql_gmv / sql_orders, 2) if sql_orders else 0.0
    check("F3 客单价 = GMV / 成交订单数", near(num(dash["trade"], "avgOrderValue"), expected_aov),
          f"客单价={num(dash['trade'], 'avgOrderValue')} 期望={expected_aov}")
    sql_new_users = int(sql(
        f"SELECT COUNT(*) FROM {DB}.sys_user WHERE deleted=0 "
        f"AND created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)"))
    check("F4 新增用户数与 SQL 一致", dash["users"]["newUserCount"] == sql_new_users,
          f"看板={dash['users']['newUserCount']} SQL={sql_new_users}")
    check("F5 复购率区间合法（0~100 或 null）",
          dash["users"]["repurchaseRate"] is None or 0 <= dash["users"]["repurchaseRate"] <= 100,
          f"复购率={dash['users']['repurchaseRate']} 复购人数={dash['users']['repeatBuyerCount']}/{dash['users']['buyerCount']}")
    check("F6 会员等级固定输出 4 档",
          len(dash["memberLevels"]) == 4 and [m["level"] for m in dash["memberLevels"]] == [0, 1, 2, 3],
          [f"{m['name']}:{m['userCount']}" for m in dash["memberLevels"]])
    check("F7 趋势点数 = 区间天数（7 天）", len(dash["trend"]) == 7, f"{len(dash['trend'])} 个点")
    check("F8 品类占比合计约 100%（有成交时）",
          (not dash["categoryShare"]) or abs(sum(c["percent"] for c in dash["categoryShare"]) - 100) < 0.5,
          f"合计={round(sum(c['percent'] for c in dash['categoryShare']), 1)}%")
    today_dash = call("GET", "/admin/stats/dashboard?range=today", None, admin_tok)
    check("F9 区间切换生效（today 的点数=1、且 GMV ≤ 7 天 GMV）",
          today_dash["days"] == 1 and len(today_dash["trend"]) == 1
          and num(today_dash["trade"], "gmv") <= num(dash["trade"], "gmv") + 0.01,
          f"today_gmv={num(today_dash['trade'], 'gmv')} 7d_gmv={num(dash['trade'], 'gmv')}")
    check("F10 秒杀效果含刚建的场次",
          any(s["id"] == sale["id"] for s in dash["flashSales"]),
          f"{len(dash['flashSales'])} 个场次")

    # ============ G. 协议 / 隐私 ============
    terms = call("GET", "/legal-docs/TERMS")
    privacy = call("GET", "/legal-docs/PRIVACY")
    check("G1 协议与隐私均公开可读且正文非空",
          len(terms["content"]) > 200 and len(privacy["content"]) > 200,
          f"用户协议 {len(terms['content'])} 字 / 隐私政策 {len(privacy['content'])} 字")
    check("G2 正文含待替换占位符（提示上线前须替换主体信息）",
          "【" in terms["content"] and "【" in privacy["content"],
          f"用户协议占位符 {terms['content'].count('【')} 处")
    check("G3 未知文档 key → 404", status_of("GET", "/legal-docs/NOT_EXIST") == 404)
    check("G4 协议正文不允许普通用户改（403）", status_of(
        "PUT", "/admin/legal-docs/TERMS", {"title": "x"}, tok) == 403)

    orig_doc_content["TERMS"] = terms["content"]
    edited = call("PUT", "/admin/legal-docs/TERMS",
                  {"content": terms["content"] + "\n【验证标记】" + STAMP}, admin_tok)
    check("G5 后台可编辑且保存生效", "【验证标记】" + STAMP in edited["content"])
    reread = call("GET", "/legal-docs/TERMS")
    check("G6 前台能读到后台改后的正文", "【验证标记】" + STAMP in reread["content"])
    admin_list = call("GET", "/admin/legal-docs", None, admin_tok)
    check("G7 后台可列出全部文档", len(admin_list) >= 2,
          [d["docKey"] for d in admin_list])

except Exception as exc:  # noqa: BLE001
    check("EXCEPTION " + repr(exc), False)

finally:
    try:
        # 还原协议正文
        if orig_doc_content.get("TERMS"):
            sql(f"UPDATE {DB}.legal_doc SET content = "
                f"'{orig_doc_content['TERMS'].replace(chr(39), chr(39) + chr(39))}' WHERE doc_key='TERMS'")
        # 删测试场次（含可能占用的名额）
        for sid in created_sales:
            sql(f"DELETE FROM {DB}.flash_sale WHERE id={sid}")
        # 断 orders ↔ user_coupon 环 → 删 user 级流水 → 删订单子表 → 删订单 → 删用户
        ids = [i for i in (uid, admin_id) if i is not None]
        idlist = ",".join(str(i) for i in ids) or "NULL"
        olist = ",".join(str(i) for i in created_orders) or "NULL"
        # 还原库存：**必须排除已取消/已关闭的订单** —— 它们已经通过取消流程回退过库存了，
        # 这里再回退一次就会让库存凭空多出来（本脚本 E1 用例取消过订单，踩过这个坑）。
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
print(f"\n==== {passed}/{len(results)} 项通过 ====")
for name, ok in results:
    if not ok:
        print("  FAILED:", name)
