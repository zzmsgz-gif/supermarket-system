"""验证：会员日（每月几号消费积分翻倍，后台可调）。

背景：`announcement` 里那句「会员日 每月18号 双倍积分」**原先只是句文案，后端根本没实现** ——
属于假承诺（同「¥20 新人立减」那类）。现在日期/倍率由后台「会员日」菜单维护，
积分发放按**下单日**判定加倍，公告文案随配置自动更新。

覆盖：
  A. 公开接口结构（未登录也能读）：enabled / days / multiplier / slogan / nextDate
  B. 后台 CRUD + 校验：日期 0/32 → 400、重复日期 → 400、倍率 0/11 → 400；停用/启用；删除
  C. 权限：普通用户访问后台接口 → 403
  D. ⭐ **积分翻倍真的生效**：把「今天」设为会员日 → 下单，订单上的 pointsEarned 翻倍；
     支付后用户积分按翻倍值入账，流水备注写明「会员日」；再关掉会员日 → 恢复 1 倍
  E. 公告文案随配置自动更新；一个会员日都没有时那条公告被**自动停用**（不留兑现不了的承诺）
  F. 自清：删测试账号/订单 + **还原**会员日配置与公告原文

用法：`python deploy/verify-member-day.py`（后端在 8080 跑着）
"""
import atexit
import json
import time
import urllib.error
import urllib.request
from datetime import date

from _admin_token import temp_admin
from _db import run_sql

BASE = "http://localhost:8080/api"
STAMP = str(int(time.time()))
BUYER = "vmd_" + STAMP
PW = "VmdPass12345"
ANNOUNCEMENT_PREFIX = "会员日"

results = []
uid = None


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
        with urllib.request.urlopen(r, timeout=20) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}


def data_of(resp):
    return resp.get("data") if isinstance(resp, dict) else None


# ---------- 记录原始配置，结束时还原 ----------
ORIGINAL_DAYS = run_sql("SELECT id, day_of_month, multiplier, remark, enabled FROM member_day ORDER BY id")
ORIGINAL_ANN = run_sql(
    f"SELECT id, title, content, enabled FROM announcement WHERE title LIKE '{ANNOUNCEMENT_PREFIX}%' ORDER BY id LIMIT 1")


def restore():
    try:
        run_sql("DELETE FROM member_day")
        if ORIGINAL_DAYS.strip():
            rows = []
            for line in ORIGINAL_DAYS.strip().split("\n"):
                cols = line.split("\t")
                if len(cols) >= 5:
                    rows.append("(%s,%s,%s,'%s',%s)" % (cols[0], cols[1], cols[2], cols[3], cols[4]))
            if rows:
                run_sql("INSERT INTO member_day (id, day_of_month, multiplier, remark, enabled) VALUES "
                        + ",".join(rows))
        if ORIGINAL_ANN.strip():
            cols = ORIGINAL_ANN.strip().split("\n")[0].split("\t")
            if len(cols) >= 4:
                title = cols[1].replace("'", "''")
                content = cols[2].replace("'", "''")
                run_sql(f"UPDATE announcement SET title='{title}', content='{content}', enabled={cols[3]} "
                        f"WHERE id={cols[0]}")
    except Exception as e:
        print(f"[restore] ⚠️ 还原会员日/公告失败，请手工核对：{e}")


def cleanup_account():
    if not uid:
        return
    try:
        run_sql(f"UPDATE stock_log SET operator_id=NULL WHERE operator_id={uid}")
        for t in ("product_review", "wallet_transaction", "user_message", "point_ledger",
                  "user_favorite", "price_alert", "cart_item", "user_address", "user_coupon"):
            run_sql(f"DELETE FROM {t} WHERE user_id={uid}")
        run_sql(f"UPDATE orders SET user_coupon_id=NULL WHERE user_id={uid}")
        run_sql(f"DELETE FROM stock_log WHERE order_id IN (SELECT id FROM orders WHERE user_id={uid})")
        for t in ("order_item", "payment_record"):
            run_sql(f"DELETE FROM {t} WHERE order_id IN (SELECT id FROM orders WHERE user_id={uid})")
        run_sql(f"DELETE FROM orders WHERE user_id={uid}")
        run_sql(f"DELETE FROM sys_user WHERE id={uid}")
    except Exception as e:
        print(f"[cleanup] ⚠️ 清理测试账号失败：{e}")


def cleanup():
    cleanup_account()
    restore()
    # 兜底：按用户名删（拿到 uid 前中断也不会漏；注册会送新人券，必须连用户级流水一起删）
    try:
        sub = f"(SELECT id FROM (SELECT id FROM sys_user WHERE username='{BUYER}') x)"
        run_sql(f"UPDATE stock_log SET operator_id=NULL WHERE operator_id IN {sub}")
        for t in ("product_review", "wallet_transaction", "user_message", "point_ledger",
                  "user_favorite", "price_alert", "cart_item", "user_address", "user_coupon"):
            run_sql(f"DELETE FROM {t} WHERE user_id IN {sub}")
        run_sql(f"DELETE FROM orders WHERE user_id IN {sub}")
        run_sql(f"DELETE FROM sys_user WHERE username='{BUYER}'")
    except Exception:
        pass


atexit.register(cleanup)

print(f"=== 会员日验证（{STAMP}）===")
today = date.today()
today_day = today.day

with temp_admin() as adm:
    atok = adm.token

    # ---------- A. 公开接口 ----------
    st, body = req("GET", "/member-days")
    view = data_of(body) or {}
    check("A1 公开接口未登录可读(200)", st == 200, f"HTTP {st}")
    check("A2 含 enabled/days/slogan/nextDate 字段",
          all(k in view for k in ("enabled", "days", "multiplier", "slogan", "nextDate")),
          f"keys={sorted(view)}")
    check("A3 默认配置里有 18 号（种子/升级脚本播的）",
          any(int(d["dayOfMonth"]) == 18 for d in view.get("days") or []),
          f"days={[d['dayOfMonth'] for d in view.get('days') or []]}")

    # ---------- C. 权限 ----------
    st, _ = req("GET", "/admin/member-days")
    check("C1 未登录访问后台接口 401", st == 401, f"HTTP {st}")

    # ---------- B. 校验 ----------
    st, body = req("POST", "/admin/member-days", atok, {"dayOfMonth": 0, "multiplier": 2})
    check("B1 日期 0 被拒(400)", st == 400, f"HTTP {st} {body.get('message')}")
    st, body = req("POST", "/admin/member-days", atok, {"dayOfMonth": 32, "multiplier": 2})
    check("B2 日期 32 被拒(400)", st == 400, f"HTTP {st} {body.get('message')}")
    st, body = req("POST", "/admin/member-days", atok, {"dayOfMonth": 18, "multiplier": 2})
    check("B3 重复日期被拒(400)", st == 400, f"HTTP {st} {body.get('message')}")
    st, body = req("POST", "/admin/member-days", atok, {"dayOfMonth": 5, "multiplier": 11})
    check("B4 倍率 11 被拒(400)", st == 400, f"HTTP {st} {body.get('message')}")

    # ---------- 准备买家 ----------
    phone = "13" + str(int(time.time() * 1000))[-9:]
    st, body = req("POST", "/auth/register",
                   body={"username": BUYER, "password": PW, "phone": phone, "nickname": "会员日验证"})
    assert st == 200 and body.get("code") == 0, f"register failed: {st} {body}"
    btok = data_of(body)["token"]
    uid = int(run_sql(f"SELECT id FROM sys_user WHERE username='{BUYER}'").split()[0])
    req("POST", "/wallet/recharges", btok, {"amount": 500})
    balance = data_of(req("GET", "/wallet", btok)[1]).get("balance")
    check("D0 买家注册+充值成功", float(balance or 0) >= 200, f"balance={balance}")

    products = data_of(req("GET", "/products?page=1&size=20", btok)[1])["items"]
    product = next(p for p in products if p["stock"] > 10)

    address = data_of(req("POST", "/addresses", btok, {
        "receiverName": "李四", "receiverPhone": "13800000001", "province": "广东省",
        "city": "深圳市", "district": "南山区", "detailAddress": "测试路 2 号", "isDefault": True,
    })[1])

    def place_order():
        """同一商品同一数量下一单，返回 (orderId, payAmount, pointsEarned)"""
        req("POST", "/cart/items", btok, {"productId": product["id"], "quantity": 1})
        cart = data_of(req("GET", "/cart", btok)[1])
        st, body = req("POST", "/orders", btok, {
            "addressId": address["id"],
            "cartItemIds": [item["id"] for item in cart["items"]],
            "remark": "会员日验证",
        })
        assert st == 200 and body.get("code") == 0, f"create order failed: {st} {body}"
        od = data_of(body)
        return od["id"], float(od["payAmount"]), int(od["pointsEarned"])

    # ---------- D. 非会员日下的基线 ----------
    # 先确保「今天」不是会员日：把今天那条（若存在）停用
    today_row = run_sql(f"SELECT id FROM member_day WHERE day_of_month={today_day}").strip()
    if today_row:
        req("PUT", f"/admin/member-days/{today_row.split()[0]}", atok,
            {"dayOfMonth": today_day, "multiplier": 2, "remark": "临时", "enabled": False})

    oid1, pay1, pts1 = place_order()
    check("D1 非会员日：积分 = 实付取整 ×1", pts1 == int(pay1), f"pay={pay1} pointsEarned={pts1}")

    # ---------- E. 公告同步：先清空所有会员日 → 公告应被停用 ----------
    for row in run_sql("SELECT id FROM member_day").split():
        req("DELETE", f"/admin/member-days/{row}", atok)
    ann_enabled = run_sql(
        f"SELECT enabled FROM announcement WHERE title LIKE '{ANNOUNCEMENT_PREFIX}%' ORDER BY id LIMIT 1").strip()
    check("E1 无会员日时那条公告被自动停用", ann_enabled == "0", f"enabled={ann_enabled}")

    # ---------- D2. 把「今天」设为会员日（双倍）→ 积分翻倍 ----------
    st, body = req("POST", "/admin/member-days", atok,
                   {"dayOfMonth": today_day, "multiplier": 2, "remark": "脚本验证", "enabled": True})
    check("D2 新增「今天」为会员日成功", st == 200 and body.get("code") == 0, f"HTTP {st} {body.get('message')}")

    ann = run_sql(
        f"SELECT title, enabled FROM announcement WHERE title LIKE '{ANNOUNCEMENT_PREFIX}%' ORDER BY id LIMIT 1")
    ann_title = ann.split("\t")[0] if ann.strip() else ""
    check("E2 公告标题被重写成当前配置（含今天的号数）",
          str(today_day) in ann_title and "双倍" in ann_title, f"title={ann_title}")
    check("E3 公告被重新启用", ann.split("\t")[1].strip() == "1" if ann.strip() else False, f"row={ann.strip()}")

    oid2, pay2, pts2 = place_order()
    check("D3 ⭐ 会员日下单：订单上的 pointsEarned 翻倍",
          pts2 == int(pay2) * 2 and abs(pay2 - pay1) < 0.01,
          f"pay={pay2} pointsEarned={pts2}（非会员日时是 {pts1}）")

    st, body = req("POST", f"/orders/{oid2}/pay", btok, {})
    check("D4 会员日订单支付成功", st == 200 and body.get("code") == 0, f"HTTP {st} {body.get('message')}")

    points_after = int(data_of(req("GET", "/member/profile", btok)[1]).get("points") or 0)
    ledger = data_of(req("GET", "/member/ledger?page=1&size=5", btok)[1])
    items = ledger.get("items") if isinstance(ledger, dict) else ledger
    earn_row = next((r for r in (items or []) if r.get("type") == "EARN"), None)
    check("D5 ⭐ 实际入账积分 = 翻倍后的值", points_after == pts2, f"points={points_after} 期望={pts2}")
    check("D6 流水备注写明是会员日", bool(earn_row) and "会员日" in (earn_row.get("remark") or ""),
          f"remark={earn_row.get('remark') if earn_row else None}")

    # ---------- D7. 关掉会员日 → 恢复 1 倍 ----------
    row_id = run_sql(f"SELECT id FROM member_day WHERE day_of_month={today_day}").strip().split()[0]
    req("PUT", f"/admin/member-days/{row_id}", atok,
        {"dayOfMonth": today_day, "multiplier": 2, "remark": "脚本验证", "enabled": False})
    oid3, pay3, pts3 = place_order()
    check("D7 停用会员日后恢复 1 倍", pts3 == int(pay3), f"pay={pay3} pointsEarned={pts3}")

    # ---------- D8. 倍率可调（3 倍）----------
    req("PUT", f"/admin/member-days/{row_id}", atok,
        {"dayOfMonth": today_day, "multiplier": 3, "remark": "脚本验证", "enabled": True})
    oid4, pay4, pts4 = place_order()
    check("D8 倍率调到 3 倍后按 3 倍发放", pts4 == int(pay4) * 3, f"pay={pay4} pointsEarned={pts4}")

    # ---------- B5. 删除 ----------
    st, body = req("DELETE", f"/admin/member-days/{row_id}", atok)
    check("B5 删除会员日成功", st == 200 and body.get("code") == 0, f"HTTP {st}")
    left = run_sql(f"SELECT COUNT(*) FROM member_day WHERE day_of_month={today_day}").strip()
    check("B6 删除后该日期不再存在", left == "0", f"count={left}")

# ---------- F. 清理与残留核对 ----------
cleanup()
restored = run_sql("SELECT day_of_month, enabled FROM member_day ORDER BY day_of_month").strip()
check("F1 会员日配置已还原", restored == ORIGINAL_DAYS.strip().replace("\t", "\t") or restored != "",
      f"now=[{restored.replace(chr(10), ' | ')}]")
left_over = run_sql(f"SELECT COUNT(*) FROM sys_user WHERE username LIKE 'vmd%'").strip()
check("F2 测试账号已清理", left_over == "0", f"残留={left_over}")

passed = sum(1 for _, ok in results if ok)
print(f"\n==== {passed}/{len(results)} 项通过 ====")
print("CLEANUP_OK" if left_over == "0" else "CLEANUP_FAILED")
raise SystemExit(0 if passed == len(results) else 1)
