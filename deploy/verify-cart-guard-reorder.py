"""购物车「失效行提前暴露」+「再来一单」端到端验证 —— 后端 API 层。

覆盖：
  A. 准备：买家注册/充值/选品（记录并最终还原被本脚本改动的商品状态与库存）
  B. 购物车提前暴露失效行：
     B1 正常时每行 onSale 为 true
     B2 后台下架后，该行 onSale 变 false（**其余行不受影响**）
     B3 对已下架商品**增加**数量 → 409，文案点明"已下架"
     B4 ⭐ 对已下架商品**减少**数量 → 必须成功。否则用户被锁在一个自己收拾不了的购物车里
     B5 提交包含已下架商品的订单 → 409，且文案**含商品名**（不再是笼统的 "Product not found"）
     B6 库存不足时购物车如实回传实时库存（前端据此提示"库存仅剩 N 件"）
     B7 库存不足下单 → 409，文案含商品名（不再是笼统的 "Insufficient stock"）
     B8 老客户端的英文错误不再出现（回归断言）
  C. 再来一单：
     C1 全部商品在售时 → addedCount = 商品数、skipped 为空、购物车数量与订单一致
     C2 订单里有商品已下架时 → **只跳过那一件**，其余照常加入，并回报商品名与原因
     C3 未登录 → 401
     C4 他人的订单 → 拒绝（订单归属校验）

自建测试账号与订单，finally 按外键顺序清理（stock_log 有 order_id / operator_id 两条外键，
必须在删 orders 与删 sys_user 之前清），并把被改动的商品状态/库存还原回脚本开始前的值。
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

buyer = "cgr_" + STAMP
PW = "Cgrtest123"
phone = "134" + STAMP[-8:]
uid = None
ORIGINAL_PRODUCT = {}
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
    p = subprocess.run([MYSQL, "-uroot", "-p" + DBPASS, "-D", "supermarket_system",
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


def watch_product(pid):
    """记录商品原始 status/stock，供 finally 还原。"""
    row = scalar(f"SELECT CONCAT_WS('|', status, stock) FROM supermarket_system.product WHERE id={pid}")
    status, stock = row.split("|")
    ORIGINAL_PRODUCT[pid] = (status, int(stock))


def cart_of(token):
    cart, _ = call("GET", "/cart", None, token)
    return cart


def item_of(cart, pid):
    return next((i for i in cart["items"] if i["productId"] == pid), None)


try:
    # ⚠️ 必须在造任何数据之前记录：stock_log 没有 user_id，只能按 id 截断来清（见 finally）
    MAX_LOG_ID = int(scalar("SELECT COALESCE(MAX(id),0) FROM supermarket_system.stock_log") or 0)

    reg = call("POST", "/auth/register", {"username": buyer, "password": PW,
                                          "nickname": "购物车体检", "phone": phone})[0]
    tok = reg["token"]
    uid = call("GET", "/auth/me", None, tok)[0]["id"]
    admin_tok = call("POST", "/auth/login", {"username": "admin", "password": "Admin123456"})[0]["token"]
    check("A0 准备：买家 + 管理员 token", bool(tok) and bool(admin_tok))

    call("POST", "/wallet/recharges", {"amount": 2000}, tok)
    check("A1 充值 2000 到账", float(call("GET", "/wallet", None, tok)[0]["balance"]) >= 2000)

    products = call("GET", "/products?page=1&size=50", None, tok)[0]["items"]
    usable = [p for p in products if p["stock"] > 10]
    pa, pb, pc = usable[0], usable[1], usable[2]
    for p in (pa, pb, pc):
        watch_product(p["id"])
    check("A2 选到 3 个库存充足的测试商品", len({pa['id'], pb['id'], pc['id']}) == 3,
          f"{pa['name']} / {pb['name']} / {pc['name']}")
    # 后续下单统一用「门店自提」：不需要收货地址，这样校验才会走到商品/库存那一层，
    # 不会被「请选择收货地址」提前挡回去（踩过：用 EXPRESS 时空手断言商品错误，必然失败）
    store = call("GET", "/stores", None, tok)[0][0]

    # ============ B. 购物车提前暴露失效行 ============
    call("POST", "/cart/items", {"productId": pa["id"], "quantity": 2}, tok)
    call("POST", "/cart/items", {"productId": pb["id"], "quantity": 1}, tok)
    cart = cart_of(tok)
    check("B1 正常商品在购物车里 onSale=true",
          item_of(cart, pa["id"])["onSale"] is True and item_of(cart, pb["id"])["onSale"] is True)

    # 模拟后台下架（真实路径是 AdminProductService，这里直接改状态，finally 会还原）
    sql(f"UPDATE supermarket_system.product SET status='OFF_SALE' WHERE id={pa['id']}")
    cart = cart_of(tok)
    check("B2 下架后该行 onSale=false，且不影响其他行",
          item_of(cart, pa["id"])["onSale"] is False and item_of(cart, pb["id"])["onSale"] is True)

    pa_item = item_of(cart, pa["id"])
    st, body = call_status("PUT", f"/cart/items/{pa_item['id']}", {"quantity": 3}, tok)
    check("B3 对已下架商品增加数量被拒(409) 且文案点明已下架",
          st == 409 and "下架" in body, f"{st} {body[:90]}")

    # ⭐ 修复点：下架商品必须还能减少数量，否则用户被锁在一个收拾不了的购物车里
    st, body = call_status("PUT", f"/cart/items/{pa_item['id']}", {"quantity": 1}, tok)
    ok_reduce = st == 200 and item_of(cart_of(tok), pa["id"])["quantity"] == 1
    check("B4 对已下架商品减少数量必须成功（否则用户无法收拾购物车）", ok_reduce, f"{st}")

    # B9 加购超库存：这条消息会被「再来一单」原样当成跳过原因回传，而前端已拼过商品名，
    #    所以它**不该**再带商品名（否则显示成「面包（商品「面包」库存不足…）」）。
    #    ⚠️ 数量要取「库存 + 5」而不是 99999：超 DTO 的 @Max 会拿到 400，测不到 409 这条业务防线。
    over = ORIGINAL_PRODUCT[pb["id"]][1] + 5
    st, body = call_status("POST", "/cart/items", {"productId": pb["id"], "quantity": over}, tok)
    check("B9 加购超库存被拒(409)，且文案不重复商品名",
          st == 409 and pb["name"] not in body, f"{st} {body[:90]}")

    cart = cart_of(tok)
    ids = [i["id"] for i in cart["items"]]
    st, body = call_status("POST", "/orders",
                           {"cartItemIds": ids, "fulfillmentType": "PICKUP",
                            "pickupStoreId": store["id"]}, tok)
    check("B5 提交含已下架商品的订单被拒(409) 且文案含商品名",
          st == 409 and pa["name"] in body, f"{st} {body[:120]}")
    check("B8 回归：不再出现笼统英文错误 Product not found / Insufficient stock",
          "Product not found" not in body and "Insufficient stock" not in body, body[:80])

    # 恢复 pa，构造「库存不足」场景：购物车里 2 件、库里只剩 1 件
    sql(f"UPDATE supermarket_system.product SET status='ON_SALE' WHERE id={pa['id']}")
    pb_item = item_of(cart_of(tok), pb["id"])
    call("PUT", f"/cart/items/{pb_item['id']}", {"quantity": 2}, tok)
    sql(f"UPDATE supermarket_system.product SET stock=1 WHERE id={pb['id']}")
    cart = cart_of(tok)
    pb_row = item_of(cart, pb["id"])
    check("B6 购物车如实回传实时库存（前端据此提示「库存仅剩 N 件」）",
          int(pb_row["stock"]) == 1 and int(pb_row["quantity"]) == 2,
          f"stock={pb_row['stock']} qty={pb_row['quantity']}")

    st, body = call_status("POST", "/orders",
                           {"cartItemIds": [i["id"] for i in cart["items"]],
                            "fulfillmentType": "PICKUP", "pickupStoreId": store["id"]}, tok)
    check("B7 库存不足下单被拒(409) 且文案含商品名",
          st == 409 and pb["name"] in body, f"{st} {body[:120]}")

    # 复原 pb 库存，进入 C 段
    sql(f"UPDATE supermarket_system.product SET stock={ORIGINAL_PRODUCT[pb['id']][1]} WHERE id={pb['id']}")
    for it in cart_of(tok)["items"]:
        call("DELETE", f"/cart/items/{it['id']}", None, tok)
    check("A3 清空购物车以便下单", not cart_of(tok)["items"])

    # ============ C. 再来一单 ============
    for p, q in ((pb, 2), (pc, 1)):
        call("POST", "/cart/items", {"productId": p["id"], "quantity": q}, tok)
    cart = cart_of(tok)
    order = call("POST", "/orders", {"cartItemIds": [i["id"] for i in cart["items"]],
                                     "fulfillmentType": "PICKUP",
                                     "pickupStoreId": store["id"]}, tok)[0]
    call("POST", f"/orders/{order['id']}/pay", None, tok)
    # ⚠️ 必须重新拉一次：下单响应拿到的是**支付前**的快照（PENDING_PAYMENT），拿它断言支付结果必错
    paid = call("GET", f"/orders/{order['id']}", None, tok)[0]
    check("C0 买家下一张已支付订单（2 种商品）", paid["status"] == "PAID",
          f"{paid['orderNo']} 状态={paid['status']}")

    res = call("POST", f"/orders/{order['id']}/reorder", None, tok)[0]
    check("C1 全部在售时再来一单：2 种全部加入、无跳过",
          res["addedCount"] == 2 and res["skippedCount"] == 0,
          f"added={res['addedCount']} skipped={res['skippedCount']}")
    cart = cart_of(tok)
    qb = item_of(cart, pb["id"])
    qc = item_of(cart, pc["id"])
    check("C1b 回填数量与订单一致",
          qb and qc and int(qb["quantity"]) == 2 and int(qc["quantity"]) == 1,
          f"{qb and qb['quantity']} / {qc and qc['quantity']}")

    # 下架其中一件，再点一次「再来一单」：应只跳过那一件
    sql(f"UPDATE supermarket_system.product SET status='OFF_SALE' WHERE id={pc['id']}")
    for it in cart_of(tok)["items"]:
        call("DELETE", f"/cart/items/{it['id']}", None, tok)
    res = call("POST", f"/orders/{order['id']}/reorder", None, tok)[0]
    skipped_names = [s["productName"] for s in res["skipped"]]
    check("C2 有商品下架时：只跳过那一件，其余照常加入",
          res["addedCount"] == 1 and res["skippedCount"] == 1 and pc["name"] in skipped_names,
          f"added={res['addedCount']} skipped={skipped_names}")
    check("C2b 跳过原因明确（含「下架」）",
          res["skippedCount"] == 1 and "下架" in res["skipped"][0]["reason"],
          res["skipped"][0]["reason"] if res["skippedCount"] else "")
    sql(f"UPDATE supermarket_system.product SET status='ON_SALE' WHERE id={pc['id']}")

    st, _ = call_status("POST", f"/orders/{order['id']}/reorder")
    check("C3 未登录再来一单被拒(401)", st == 401, st)

    st, _ = call_status("POST", f"/orders/{order['id']}/reorder", None, admin_tok)
    check("C4 不能对他人的订单再来一单（归属校验）", st in (403, 404), st)

finally:
    try:
        if uid is not None:
            for pid, (status, stock) in ORIGINAL_PRODUCT.items():
                sql(f"UPDATE supermarket_system.product SET status='{status}', stock={stock} WHERE id={pid}")
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
            check("Z1 清理后无残留账号", scalar(f"SELECT COUNT(*) FROM supermarket_system.sys_user WHERE id={uid}") == "0")
            check("Z2 清理后无残留订单", scalar(f"SELECT COUNT(*) FROM supermarket_system.orders WHERE user_id={uid}") == "0")
            check("Z3 商品状态/库存已还原",
                  all(scalar(f"SELECT CONCAT_WS('|', status, stock) FROM supermarket_system.product WHERE id={pid}")
                      == f"{s}|{st}" for pid, (s, st) in ORIGINAL_PRODUCT.items()))
        print("CLEANUP_OK")
    except Exception as exc:  # noqa: BLE001
        print("CLEANUP_FAIL:", exc)

passed = sum(1 for _, ok in results if ok)
print(f"\n==== {passed}/{len(results)} 项通过 ====")
for name, ok in results:
    if not ok:
        print("  FAILED:", name)
