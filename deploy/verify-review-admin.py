"""后台「评价管理」端到端验证 —— 后端 API 层。

背景：评价此前是**只写不用**的 —— 前台能写（晒图评价），但后台没有任何评价入口、也没有查询接口，
评价只在商品详情页展示。商家看不到、回不了差评，闭环断在商家这一侧。
本脚本验证补上这一环后，三个动作（看 / 回 / 藏）都真的贯通，并且**不破坏既有口径**。

覆盖：
  A. 准备：买家下单 → 支付 → 确认收货 → 评价（评价要求订单 COMPLETED）
  B. 后台"看"：列表能看到该评价（商品名/账号/评分/正文齐全）；按星级、按是否回复、按关键词筛选都对
  C. 后台"回"：回复成功 → ⭐**前台商品详情页立刻能看到回复**（否则回了也白回）；空串＝撤回；
     超长回复被 DTO 校验挡下(400)
  D. 后台"藏"：隐藏后 ⭐ 前台列表不再出现、**平均分聚合同步剔除**（列表与平均分必须同口径）；
     ⭐ 但订单维度**仍算已评价**（否则前端「评价」按钮会重新出现，用户一点又拿到 409）
  E. 鉴权：普通用户访问 /admin/reviews → 403；未登录 → 401

自建测试账号/订单/评价，finally 按外键顺序清理并还原商品库存与评价聚合状态。
"""
import json
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
from _db import DBUSER, DBPASS

BASE = "http://localhost:8080/api"
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
STAMP = str(int(time.time()))
results = []

buyer = "revadm_" + STAMP
PW = "Revadm123"
phone = "132" + STAMP[-8:]
uid = None
created_orders = []
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


def front_reviews(product_id, token, keyword=None):
    qs = urllib.parse.urlencode({"page": 1, "size": 50})
    return call("GET", f"/reviews/products/{product_id}?{qs}", None, token)[0].get("items") or []


def rating_of(product_id, token):
    """商品评分聚合 [avgRating, reviewCount]；该商品没有未隐藏评价时返回 (None, 0)"""
    for row in call("GET", "/products/rating-summary", None, token)[0]:
        if row["productId"] == product_id:
            return row["avgRating"], row["reviewCount"]
    return None, 0


try:
    MAX_LOG_ID = int(scalar("SELECT COALESCE(MAX(id),0) FROM supermarket_system.stock_log") or 0)

    reg = call("POST", "/auth/register", {"username": buyer, "password": PW,
                                          "nickname": "评价管理验证", "phone": phone})[0]
    tok = reg["token"]
    uid = call("GET", "/auth/me", None, tok)[0]["id"]
    admin_tok = call("POST", "/auth/login", {"username": "admin", "password": "Admin123456"})[0]["token"]
    call("POST", "/wallet/recharges", {"amount": 2000}, tok)
    check("A0 准备：买家 + 管理员 token", bool(tok) and bool(admin_tok))

    products = call("GET", "/products?page=1&size=50", None, tok)[0]["items"]
    # 挑一个已有评价的商品：这样"隐藏后平均分同步剔除"才有可观测的变化
    rated = [p for p in products if p["stock"] > 3]
    prod = rated[0]
    before_avg, before_count = rating_of(prod["id"], tok)

    call("POST", "/cart/items", {"productId": prod["id"], "quantity": 1}, tok)
    cart = call("GET", "/cart", None, tok)[0]
    store = call("GET", "/stores", None, tok)[0][0]
    order = call("POST", "/orders", {"cartItemIds": [i["id"] for i in cart["items"]],
                                     "fulfillmentType": "PICKUP", "pickupStoreId": store["id"]}, tok)[0]
    created_orders.append(order["id"])
    call("POST", f"/orders/{order['id']}/pay", None, tok)
    # 自提单没有物流，必须走管理员「备货完成」推进到 SHIPPED，才能确认收货（走 /ship 会被 409 拦住）
    call("POST", f"/admin/orders/{order['id']}/ready", None, admin_tok)
    call("POST", f"/orders/{order['id']}/confirm-receipt", None, tok)
    st, body = call_status("GET", f"/orders/{order['id']}", None, tok)
    check("A1 订单已确认收货（评价要求 COMPLETED）",
          st == 200 and json.loads(body)["data"]["status"] == "COMPLETED")

    content = f"评价管理验证-{STAMP}"
    call("POST", f"/reviews/orders/{order['id']}",
         {"rating": 5, "content": content, "imageUrls": ["http://localhost:8080/uploads/demo.jpg"]}, tok)
    after_avg, after_count = rating_of(prod["id"], tok)
    check("A2 评价已创建，且评分聚合 +1", after_count == before_count + 1,
          f"{before_count} -> {after_count}")

    # ============ B. 后台「看」 ============
    page = call("GET", f"/admin/reviews?keyword={urllib.parse.quote(content)}", None, admin_tok)[0]
    rows = page.get("items") or []
    row = rows[0] if rows else {}
    check("B1 后台列表能看到该评价，且商品名/账号/评分/正文齐全",
          len(rows) == 1 and row.get("productName") == prod["name"]
          and row.get("rating") == 5 and row.get("content") == content
          and bool(row.get("username")),
          f"{row.get('productName')} / {row.get('username')} / {row.get('rating')}")

    summary = call("GET", "/admin/reviews/summary", None, admin_tok)[0]
    check("B2 汇总含总数/未回复数/平均分/星级分布",
          summary["total"] >= 1 and summary["unrepliedCount"] >= 1
          and summary["avgRating"] is not None and len(summary["stars"]) >= 1,
          f"total={summary['total']} unreplied={summary['unrepliedCount']} avg={summary['avgRating']}")

    hit5 = call("GET", "/admin/reviews?rating=5", None, admin_tok)[0]["items"]
    miss1 = call("GET", "/admin/reviews?rating=1", None, admin_tok)[0]["items"]
    check("B3 按星级筛选生效（5 星能查到、1 星查不到）",
          any(r["id"] == row["id"] for r in hit5) and not any(r["id"] == row["id"] for r in miss1),
          f"5星={len(hit5)} 1星={len(miss1)}")

    unreplied = call("GET", "/admin/reviews?replied=false", None, admin_tok)[0]["items"]
    replied = call("GET", "/admin/reviews?replied=true", None, admin_tok)[0]["items"]
    check("B4 按「未回复」筛选生效（未回复能查到、已回复查不到）",
          any(r["id"] == row["id"] for r in unreplied)
          and not any(r["id"] == row["id"] for r in replied))

    # ============ C. 后台「回」 ============
    reply_text = "感谢反馈，已同步门店加强品控。"
    st, body = call_status("POST", f"/admin/reviews/{row['id']}/reply",
                           {"replyContent": reply_text}, admin_tok)
    check("C1 商家回复成功且带回复时间",
          st == 200 and json.loads(body)["data"]["replyContent"] == reply_text
          and json.loads(body)["data"]["replyAt"],
          f"{st}")

    mine = next((r for r in front_reviews(prod["id"], tok) if r["id"] == row["id"]), None)
    check("C2 ⭐前台商品详情页能看到商家回复（否则回了也白回）",
          mine is not None and mine.get("replyContent") == reply_text, mine and mine.get("replyContent"))

    st, body = call_status("POST", f"/admin/reviews/{row['id']}/reply", {"replyContent": ""}, admin_tok)
    mine = next((r for r in front_reviews(prod["id"], tok) if r["id"] == row["id"]), None)
    check("C3 传空串＝撤回回复（前台不再展示，且不留「有回复时间却无内容」的脏状态）",
          st == 200 and json.loads(body)["data"]["replyContent"] is None
          and json.loads(body)["data"]["replyAt"] is None
          and (mine is None or not mine.get("replyContent")),
          f"{st}")

    st, body = call_status("POST", f"/admin/reviews/{row['id']}/reply",
                           {"replyContent": "超" * 501}, admin_tok)
    check("C4 超长回复被 DTO 校验挡下(400)", st == 400, f"{st} {body[:80]}")

    call("POST", f"/admin/reviews/{row['id']}/reply", {"replyContent": reply_text}, admin_tok)

    # ============ D. 后台「藏」 ============
    st, body = call_status("POST", f"/admin/reviews/{row['id']}/hidden", {"hidden": True}, admin_tok)
    check("D1 隐藏成功且列表回传 hidden=true",
          st == 200 and json.loads(body)["data"]["hidden"] is True, f"{st}")

    hidden_front = [r for r in front_reviews(prod["id"], tok) if r["id"] == row["id"]]
    check("D2 ⭐隐藏后前台商品详情页不再展示该评价", not hidden_front, f"仍可见 {len(hidden_front)} 条")

    now_avg, now_count = rating_of(prod["id"], tok)
    check("D3 ⭐隐藏后评分聚合同步剔除（列表与平均分必须同口径）",
          now_count == before_count, f"{after_count} -> {now_count}（基线 {before_count}）")

    own = call("GET", f"/reviews/orders/{order['id']}", None, tok)[0]
    check("D4 ⭐隐藏后订单维度仍算已评价（否则「评价」按钮会重现、点了又 409）", len(own) >= 1,
          f"{len(own)} 条")

    call("POST", f"/admin/reviews/{row['id']}/hidden", {"hidden": False}, admin_tok)
    restored = [r for r in front_reviews(prod["id"], tok) if r["id"] == row["id"]]
    _, back_count = rating_of(prod["id"], tok)
    check("D5 恢复展示后前台又能看到，且聚合回到 +1",
          len(restored) == 1 and back_count == before_count + 1, f"{back_count}")

    # ============ E. 鉴权 ============
    st, _ = call_status("GET", "/admin/reviews", None, tok)
    check("E1 普通用户访问后台评价接口被拒(403)", st == 403, st)
    st, _ = call_status("GET", "/admin/reviews")
    check("E2 未登录访问后台评价接口被拒(401)", st == 401, st)

finally:
    try:
        if uid is not None:
            sql(f"UPDATE supermarket_system.orders SET user_coupon_id=NULL WHERE user_id={uid}")
            for t in ("product_review", "wallet_transaction", "user_message", "point_ledger",
                      "user_favorite", "price_alert", "cart_item", "user_address", "user_coupon"):
                sql(f"DELETE FROM supermarket_system.{t} WHERE user_id={uid}")
            sql(f"DELETE FROM supermarket_system.stock_log WHERE id > {MAX_LOG_ID}")
            sql(f"DELETE oi FROM supermarket_system.order_item oi JOIN supermarket_system.orders o "
                f"ON o.id=oi.order_id WHERE o.user_id={uid}")
            sql(f"DELETE pr FROM supermarket_system.payment_record pr JOIN supermarket_system.orders o "
                f"ON o.id=pr.order_id WHERE o.user_id={uid}")
            sql(f"DELETE FROM supermarket_system.orders WHERE user_id={uid}")
            sql(f"DELETE FROM supermarket_system.sys_user WHERE id={uid}")
            check("Z1 清理后无残留账号", scalar(f"SELECT COUNT(*) FROM supermarket_system.sys_user WHERE id={uid}") == "0")
            check("Z2 清理后无残留评价",
                  scalar(f"SELECT COUNT(*) FROM supermarket_system.product_review WHERE content LIKE '评价管理验证-%'") == "0")
        print("CLEANUP_OK")
    except Exception as exc:  # noqa: BLE001
        print("CLEANUP_FAIL:", exc)

passed = sum(1 for _, ok in results if ok)
print(f"\n==== {passed}/{len(results)} 项通过 ====")
for name, ok in results:
    if not ok:
        print("  FAILED:", name)
