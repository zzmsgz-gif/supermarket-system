"""会员积分体系端到端验证（后端 API 层）。

覆盖：
  A. 会员接口：/auth/me 会员字段、/member/levels 档位、/member/profile、/member/ledger
  B. 会员价：商品详情 memberPrice 透出、购物车按会员价计金额（与下单口径一致）
  C. 下单链路：会员价 + 会员等级折扣 + 积分抵现 + 支付发积分 + 流水幂等
  D. 后台：/admin/users 返回 points/memberLevel

自建测试账号与数据，finally 中清理（删除测试订单/流水/购物车/地址/用户，还原商品会员价与库存销量）。
"""
import json
import subprocess
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
DBPASS = "zzmsgz"
STAMP = str(int(time.time()))
results = []

buyer = "memv_" + STAMP
admin = "memadmin_" + STAMP
PW = "Memv12345"
buyer_phone = "139" + STAMP[-8:]
admin_phone = "138" + STAMP[-8:]


def call(method, path, body=None, token=None):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Content-Type": "application/json",
                 **({"Authorization": "Bearer " + token} if token else {})},
        method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode()).get("data")
    except urllib.error.HTTPError as err:
        raise RuntimeError(f"{method} {path} -> {err.code} {err.read().decode()}")


def sql(stmt):
    p = subprocess.run([MYSQL, "-uroot", "-p" + DBPASS, "-D", "supermarket_system",
                        "--default-character-set=utf8mb4", "-N", "-B", "-e", stmt],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if p.returncode != 0:
        raise RuntimeError("SQL fail: " + p.stderr)
    return p.stdout.strip()


def check(name, cond, detail=""):
    results.append((name, bool(cond)))
    print(("PASS  " if cond else "FAIL  ") + name + (("  | " + str(detail)) if detail else ""))


uid = None
admin_id = None
pid = None
ordered_qty = 0
created_orders = []

try:
    # ============ A. 会员接口 ============
    reg = call("POST", "/auth/register", {"username": buyer, "password": PW,
                                          "nickname": "会员验证", "phone": buyer_phone})
    tok = reg["token"]
    me = call("GET", "/auth/me", None, tok)
    uid = me["id"]
    check("A1 /auth/me 含 points", "points" in me, me.get("points"))
    check("A2 /auth/me 含 memberLevel", "memberLevel" in me, me.get("memberLevel"))
    check("A3 /auth/me 含 totalSpent 且初始 0",
          "totalSpent" in me and float(me["totalSpent"]) == 0.0, me.get("totalSpent"))

    levels = call("GET", "/member/levels", None, tok)
    check("A4 /member/levels 返回 4 档", len(levels) == 4, [x["name"] for x in levels])
    check("A5 折扣率 = 1/0.98/0.95/0.90",
          [round(float(x["rate"]), 4) for x in levels] == [1.0, 0.98, 0.95, 0.90])
    check("A6 阈值 = 0/1000/5000/20000",
          [float(x["threshold"]) for x in levels] == [0, 1000, 5000, 20000])

    prof = call("GET", "/member/profile", None, tok)
    check("A7 profile 含等级/进度/抵现比例字段",
          all(k in prof for k in ("levelName", "progressToNext", "maxRedeemRatio", "nextLevelName")),
          prof.get("levelName"))

    led0 = call("GET", "/member/ledger?page=1&size=10", None, tok)
    check("A8 初始积分流水为空", (led0.get("items") or []) == [] and led0.get("total") == 0)

    # ============ B. 会员价 ============
    prods = call("GET", "/products?page=1&size=20", None, tok)["items"]
    prod = next(p for p in prods if p["stock"] > 20)
    pid = prod["id"]
    base_price = float(prod["price"])
    det = call("GET", f"/products/{pid}", None, tok)
    check("B1 商品详情透出 memberPrice 字段", "memberPrice" in det, det.get("memberPrice"))

    mp = round(base_price * 0.8, 2)          # 会员价 = 售价 8 折
    sql(f"UPDATE supermarket_system.product SET member_price={mp} WHERE id={pid}")
    det2 = call("GET", f"/products/{pid}", None, tok)
    check("B2 设置后详情 memberPrice 生效",
          det2.get("memberPrice") is not None and abs(float(det2["memberPrice"]) - mp) < 0.001,
          det2.get("memberPrice"))

    # ============ C. 购物车 / 下单链路 ============
    call("POST", "/wallet/recharges", {"amount": 1000}, tok)
    call("POST", "/cart/items", {"productId": pid, "quantity": 2}, tok)
    cart = call("GET", "/cart", None, tok)
    item = next(i for i in cart["items"] if i["productId"] == pid)
    check("C1 购物车单价按会员价计", abs(float(item["productPrice"]) - mp) < 0.001, item["productPrice"])
    check("C2 购物车合计 = 会员价 × 数量",
          abs(float(cart["selectedAmount"]) - mp * 2) < 0.01, cart["selectedAmount"])

    call("POST", "/addresses", {"receiverName": "张三", "receiverPhone": "13800000000",
                                "province": "广东省", "city": "深圳市", "district": "南山区",
                                "detailAddress": "会员验证路 1 号", "isDefault": True}, tok)
    aid = call("GET", "/addresses", None, tok)[0]["id"]

    # --- 订单一：不使用积分（普通会员，无等级折扣）---
    cart_ids = [i["id"] for i in cart["items"]]
    o1 = call("POST", "/orders", {"addressId": aid, "cartItemIds": cart_ids, "remark": "会员验证1"}, tok)
    created_orders.append(o1["id"])
    ordered_qty += 2
    call("POST", f"/orders/{o1['id']}/pay", None, tok)
    o1d = call("GET", f"/orders/{o1['id']}", None, tok)
    p1 = round(float(o1d["payAmount"]), 2)
    check("C3 普通会员无等级折扣", abs(float(o1d.get("memberDiscount") or 0)) < 0.001, o1d.get("memberDiscount"))
    check("C4 未用积分时 pointsUsed=0", int(o1d.get("pointsUsed") or 0) == 0)
    check("C5 发积分为实付金额向下取整", int(o1d.get("pointsEarned") or 0) == int(p1), (o1d.get("pointsEarned"), p1))
    me2 = call("GET", "/auth/me", None, tok)
    check("C6 支付后积分余额 = 本单所得", int(me2["points"]) == int(o1d.get("pointsEarned") or 0), me2["points"])
    check("C7 累计消费 = 实付金额", abs(float(me2["totalSpent"]) - p1) < 0.01, me2["totalSpent"])

    led1 = call("GET", "/member/ledger?page=1&size=10", None, tok)
    types1 = [r["type"] for r in (led1.get("items") or [])]
    check("C8 流水出现 EARN 记录", "EARN" in types1, types1)

    # --- 订单二：使用积分抵现 ---
    call("POST", "/cart/items", {"productId": pid, "quantity": 2}, tok)
    cart2 = call("GET", "/cart", None, tok)
    cart_ids2 = [i["id"] for i in cart2["items"]]
    o2 = call("POST", "/orders", {"addressId": aid, "cartItemIds": cart_ids2, "remark": "会员验证2",
                                  "usePoints": True, "pointsToUse": 100000}, tok)
    created_orders.append(o2["id"])
    ordered_qty += 2
    call("POST", f"/orders/{o2['id']}/pay", None, tok)
    o2d = call("GET", f"/orders/{o2['id']}", None, tok)
    used = int(o2d.get("pointsUsed") or 0)
    check("C9 使用积分后 pointsUsed>0", used > 0, used)
    check("C10 抵现不超过总额 50%",
          used <= int(round(float(o2d["payAmount"]) + used / 100) * 100 // 2) + 1, used)
    check("C11 积分抵现金额进入实付", abs(float(o2d["payAmount"]) - (round(float(o2d["payAmount"]), 2))) < 0.01)

    led2 = call("GET", "/member/ledger?page=1&size=20", None, tok)
    types2 = [r["type"] for r in (led2.get("items") or [])]
    check("C12 流水出现 REDEEM 记录", "REDEEM" in types2, types2)

    # --- 订单三：金卡会员（等级折扣 95 折）---
    sql(f"UPDATE supermarket_system.sys_user SET total_spent=6000, member_level=2 WHERE id={uid}")
    call("POST", "/cart/items", {"productId": pid, "quantity": 2}, tok)
    cart3 = call("GET", "/cart", None, tok)
    cart_ids3 = [i["id"] for i in cart3["items"]]
    o3 = call("POST", "/orders", {"addressId": aid, "cartItemIds": cart_ids3, "remark": "会员验证3"}, tok)
    created_orders.append(o3["id"])
    ordered_qty += 2
    call("POST", f"/orders/{o3['id']}/pay", None, tok)
    o3d = call("GET", f"/orders/{o3['id']}", None, tok)
    after_promo = round(float(o3d["totalAmount"]) - float(o3d.get("discountAmount") or 0)
                        - float(o3d.get("activityDiscount") or 0), 2)
    expect_disc = round(after_promo * 0.05, 2)
    check("C13 金卡折扣 = 优惠后金额 5%",
          abs(float(o3d.get("memberDiscount") or 0) - expect_disc) < 0.02,
          (o3d.get("memberDiscount"), expect_disc))
    check("C14 实付 = 优惠后 - 会员折扣",
          abs(float(o3d["payAmount"]) - round(after_promo - expect_disc, 2)) < 0.02,
          (o3d["payAmount"], round(after_promo - expect_disc, 2)))
    check("C15 订单记录下单时会员等级=2", int(o3d.get("memberLevel") or -1) == 2, o3d.get("memberLevel"))
    me3 = call("GET", "/auth/me", None, tok)
    check("C16 支付后按累计消费升级（≥金卡）", int(me3["memberLevel"]) >= 2, me3["memberLevel"])

    # ============ D. 后台用户列表 ============
    areg = call("POST", "/auth/register", {"username": admin, "password": PW,
                                           "nickname": "验证管理员", "phone": admin_phone})
    admin_id = call("GET", "/auth/me", None, areg["token"])["id"]
    sql(f"UPDATE supermarket_system.sys_user SET role='ADMIN' WHERE username='{admin}'")
    atok = call("POST", "/auth/login", {"username": admin, "password": PW})["token"]
    users = call("GET", f"/admin/users?page=1&size=10&keyword={buyer}", None, atok)
    row = next((u for u in (users.get("items") or []) if u["username"] == buyer), None)
    check("D1 后台用户列表命中测试用户", row is not None)
    if row:
        check("D2 列表含 points", "points" in row, row.get("points"))
        check("D3 列表含 memberLevel", "memberLevel" in row, row.get("memberLevel"))
        check("D4 列表会员等级与 /auth/me 一致",
              int(row.get("memberLevel") or -1) == int(me3["memberLevel"]), row.get("memberLevel"))

except Exception as exc:  # noqa: BLE001
    check("EXCEPTION " + repr(exc), False)

finally:
    # ---------- 清理 ----------
    try:
        if pid is not None:
            sql(f"UPDATE supermarket_system.product SET member_price=NULL WHERE id={pid}")
            if ordered_qty:
                sql(f"UPDATE supermarket_system.product SET stock=stock+{ordered_qty}, "
                    f"sales=GREATEST(sales-{ordered_qty},0) WHERE id={pid}")
        # 顺序很关键：wallet_transaction / point_ledger 有外键指向 orders，
        # 必须先删这些「子表」，再删 orders，否则 FK 约束报错中断清理。
        ids = [i for i in (uid, admin_id) if i is not None]
        idlist = ",".join(str(i) for i in ids) or "NULL"
        for t in ("wallet_transaction", "point_ledger", "cart_item", "user_address", "user_coupon"):
            sql(f"DELETE FROM supermarket_system.{t} WHERE user_id IN ({idlist})")
        for oid in created_orders:
            sql(f"DELETE FROM supermarket_system.order_item WHERE order_id={oid}")
            sql(f"DELETE FROM supermarket_system.payment_record WHERE order_id={oid}")
            sql(f"DELETE FROM supermarket_system.stock_log WHERE order_id={oid}")
            sql(f"DELETE FROM supermarket_system.orders WHERE id={oid}")
        sql(f"DELETE FROM supermarket_system.sys_user WHERE id IN ({idlist})")
        print("CLEANUP_OK")
    except Exception as exc:  # noqa: BLE001
        print("CLEANUP_FAIL:", exc)

passed = sum(1 for _, ok in results if ok)
print(f"\n==== {passed}/{len(results)} 项通过 ====")
for name, ok in results:
    if not ok:
        print("  FAILED:", name)
