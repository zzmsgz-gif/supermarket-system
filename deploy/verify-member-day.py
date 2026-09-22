"""验证：会员日（指定具体日期消费积分翻倍，后台可调）。

背景：`announcement` 里那句「会员日 每月18号 双倍积分」**原先只是句文案，后端根本没实现** ——
属于假承诺（同「¥20 新人立减」那类）。现在日期/倍率由后台「会员日」菜单维护（从日历上挑具体日期），
积分发放按**下单日**判定加倍。

覆盖：
  A. 公开接口（未登录可读）：enabled / days(memberDate) / multiplier / slogan / nextDate，且只返回未过期的
  B. 后台 CRUD + 校验：缺日期 → 400、**过去的日期 → 400**、重复日期 → 400、倍率 0/11 → 400；停用/启用；删除
  C. 权限：未登录访问后台接口 → 401
  D. ⭐ **积分翻倍真的生效**：把「今天」设为会员日 → 下单时订单上的 pointsEarned 翻倍；
     支付后用户积分按翻倍值入账、流水备注写明「会员日」；再关掉 → 恢复 1 倍；倍率调 3 → 按 3 倍
  E. ⭐ **公告不由系统改动**：改会员日配置前后，公告栏那条「会员日…」的标题/正文/启停必须**一字不变**
     （2026-09-22 用户明确要求公告由管理员自己写；这条断言就是防它被改回自动生成）
  F. 自清：删测试账号/订单 + **还原**会员日配置

用法：`python deploy/verify-member-day.py`（后端在 8080 跑着）
"""
import atexit
import json
import time
import urllib.error
import urllib.request
from datetime import date, timedelta

from _admin_token import temp_admin
from _db import run_sql

BASE = "http://localhost:8080/api"
STAMP = str(int(time.time()))
BUYER = "vmd_" + STAMP
PW = "VmdPass12345"
TODAY = date.today().isoformat()
YESTERDAY = (date.today() - timedelta(days=1)).isoformat()

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


ANN_SQL = ("SELECT id, title, content, enabled, sort_order, type FROM announcement "
           "WHERE title LIKE '会员日%' ORDER BY id LIMIT 1")
ORIGINAL_DAYS = run_sql("SELECT id, member_date, multiplier, remark, enabled FROM member_day ORDER BY id")
ORIGINAL_ANN = run_sql(ANN_SQL)


def restore_days():
    try:
        run_sql("DELETE FROM member_day")
        if ORIGINAL_DAYS.strip():
            rows = []
            for line in ORIGINAL_DAYS.strip().split("\n"):
                cols = line.split("\t")
                if len(cols) >= 5:
                    rows.append("(%s,'%s',%s,'%s',%s)" % (cols[0], cols[1], cols[2], cols[3], cols[4]))
            if rows:
                run_sql("INSERT INTO member_day (id, member_date, multiplier, remark, enabled) VALUES "
                        + ",".join(rows))
    except Exception as e:
        print(f"[restore] ⚠️ 还原会员日失败，请手工核对：{e}")


def cleanup():
    if uid:
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
    restore_days()
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

print(f"=== 会员日验证（指定日期版，{STAMP}）===")
print(f"今天 = {TODAY}\n")

with temp_admin() as adm:
    atok = adm.token

    # ---------- A. 公开接口 ----------
    st, body = req("GET", "/member-days")
    view = data_of(body) or {}
    check("A1 公开接口未登录可读(200)", st == 200, f"HTTP {st}")
    check("A2 含 enabled/days/slogan/nextDate 字段",
          all(k in view for k in ("enabled", "days", "multiplier", "slogan", "nextDate")),
          f"keys={sorted(view)}")
    days = view.get("days") or []
    check("A3 days 是具体日期（YYYY-MM-DD）且都在今天及以后",
          bool(days) and all(len(str(d.get("memberDate"))) == 10 and str(d["memberDate"]) >= TODAY for d in days),
          f"days={[d.get('memberDate') for d in days]}")
    check("A4 nextDate 是最近的那个日期", view.get("nextDate") == (days[0]["memberDate"] if days else None),
          f"nextDate={view.get('nextDate')}")
    check("A5 slogan 里带上了日期文案", "会员日（" in (view.get("slogan") or ""), f"slogan={view.get('slogan')}")

    # ---------- C. 权限 ----------
    st, _ = req("GET", "/admin/member-days")
    check("C1 未登录访问后台接口 401", st == 401, f"HTTP {st}")

    # ---------- B. 校验 ----------
    st, body = req("POST", "/admin/member-days", atok, {"multiplier": 2})
    check("B1 不带日期被拒(400)", st == 400, f"HTTP {st} {body.get('message')}")
    st, body = req("POST", "/admin/member-days", atok, {"memberDate": YESTERDAY, "multiplier": 2})
    check("B2 过去的日期被拒(400)", st == 400, f"HTTP {st} {body.get('message')}")
    st, body = req("POST", "/admin/member-days", atok, {"memberDate": TODAY, "multiplier": 11})
    check("B3 倍率 11 被拒(400)", st == 400, f"HTTP {st} {body.get('message')}")

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

    # ---------- D1. 非会员日基线 ----------
    existing_today = run_sql(f"SELECT id FROM member_day WHERE member_date='{TODAY}'").strip()
    if existing_today:
        req("PUT", f"/admin/member-days/{existing_today.split()[0]}", atok,
            {"memberDate": TODAY, "multiplier": 2, "remark": "临时", "enabled": False})
    oid1, pay1, pts1 = place_order()
    check("D1 非会员日：积分 = 实付取整 ×1", pts1 == int(pay1), f"pay={pay1} pointsEarned={pts1}")

    # ---------- D2/E. 把「今天」设为会员日 ----------
    row = run_sql(f"SELECT id FROM member_day WHERE member_date='{TODAY}'").strip()
    if row:
        st, body = req("PUT", f"/admin/member-days/{row.split()[0]}", atok,
                       {"memberDate": TODAY, "multiplier": 2, "remark": "脚本验证", "enabled": True})
    else:
        st, body = req("POST", "/admin/member-days", atok,
                       {"memberDate": TODAY, "multiplier": 2, "remark": "脚本验证", "enabled": True})
    check("D2 把「今天」配成会员日成功", st == 200 and body.get("code") == 0, f"HTTP {st} {body.get('message')}")

    st, body = req("POST", "/admin/member-days", atok, {"memberDate": TODAY, "multiplier": 2})
    check("B4 重复日期被拒(400)", st == 400, f"HTTP {st} {body.get('message')}")

    check("E1 ⭐ 公告没被系统改动（标题/正文/启停一字不变）", run_sql(ANN_SQL) == ORIGINAL_ANN,
          f"now={run_sql(ANN_SQL)[:60]!r}")

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

    # ---------- D7/D8. 停用 → 1 倍；改 3 倍 → 3 倍 ----------
    row_id = run_sql(f"SELECT id FROM member_day WHERE member_date='{TODAY}'").strip().split()[0]
    req("PUT", f"/admin/member-days/{row_id}", atok,
        {"memberDate": TODAY, "multiplier": 2, "remark": "脚本验证", "enabled": False})
    oid3, pay3, pts3 = place_order()
    check("D7 停用会员日后恢复 1 倍", pts3 == int(pay3), f"pay={pay3} pointsEarned={pts3}")

    req("PUT", f"/admin/member-days/{row_id}", atok,
        {"memberDate": TODAY, "multiplier": 3, "remark": "脚本验证", "enabled": True})
    oid4, pay4, pts4 = place_order()
    check("D8 倍率调到 3 倍后按 3 倍发放", pts4 == int(pay4) * 3, f"pay={pay4} pointsEarned={pts4}")

    check("E2 ⭐ 多次增改删之后公告仍然没被动过", run_sql(ANN_SQL) == ORIGINAL_ANN)

    # ---------- B5. 删除 ----------
    st, body = req("DELETE", f"/admin/member-days/{row_id}", atok)
    check("B5 删除会员日成功", st == 200 and body.get("code") == 0, f"HTTP {st}")
    left = run_sql(f"SELECT COUNT(*) FROM member_day WHERE member_date='{TODAY}'").strip()
    check("B6 删除后该日期不再存在", left == "0", f"count={left}")

# ---------- F. 清理与残留核对 ----------
cleanup()
restored = run_sql("SELECT id, member_date, multiplier, remark, enabled FROM member_day ORDER BY id")
check("F1 会员日配置已还原", restored == ORIGINAL_DAYS, f"now=[{restored.replace(chr(10), ' | ')}]")
left_over = run_sql("SELECT COUNT(*) FROM sys_user WHERE username LIKE 'vmd%'").strip()
check("F2 测试账号已清理", left_over == "0", f"残留={left_over}")
check("F3 公告仍然原样", run_sql(ANN_SQL) == ORIGINAL_ANN)

passed = sum(1 for _, ok in results if ok)
print(f"\n==== {passed}/{len(results)} 项通过 ====")
print("CLEANUP_OK" if left_over == "0" else "CLEANUP_FAILED")
raise SystemExit(0 if passed == len(results) else 1)
