"""方案D（C+B 组合：申请即冻结 + 退款缺口现金扣回）本地端到端验证。

目标：在本地后端(localhost:8080) + 本地 DB 上验证三件事：
  ① C（申请即冻结）：申请退款后冻结 = 本次发放积分，可用积分清零；且冻结的积分下单抵扣时不可用。
  ② B（现金扣回）：申请前已把积分花掉的，退款时按「1 积分 = 1/100 元」从现金退款等价扣回。
  ③ 驳回即释放冻结：reject 后冻结清零、积分余额与可用积分复原。

做法：
  · 买家侧全部走公开 API；退款审核需要 ADMIN 令牌，采用「注册临时买家 → 提权 ADMIN → 用后即删」。
  · 冻结/积分/退款额核验直接查本地 DB（sys_user.frozen_points / point_ledger / return_request.frozen_points）。
  · 测试账号与订单在最后全部清理（FK 顺序：子表 → orders → sys_user）。

运行：后端须在 localhost:8080 已启动且连本地 supermarket_system（已执行 upgrade-2026-10-21-freeze-points.sql）。
"""
import json
import random
import subprocess
import sys
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"
MYSQL = ["C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql", "-uroot", "-pzzmsgz", "-N", "-B",
         "supermarket_system", "--default-character-set=utf8mb4"]
TS = time.strftime("%m%d%H%M%S")
PREFIX = "vfreeze"
U1 = f"{PREFIX}_c_{TS}"      # 场景① 买家
U2 = f"{PREFIX}_b_{TS}"      # 场景② 买家
U3 = f"{PREFIX}_r_{TS}"      # 场景③ 买家
ADM = f"{PREFIX}_a_{TS}"     # 临时管理员
PWD = "Test123456"


def api(method, path, body=None, token=None, _attempt=0):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode() or "{}")
        except Exception:
            return {"code": e.code, "message": e.reason}
    except urllib.error.URLError as e:
        if _attempt >= 2:
            raise
        print(f"[retry] {method} {path} 连接重置({e})，1.5s 后重试 {_attempt + 1}/2")
        time.sleep(1.5)
        return api(method, path, body, token, _attempt + 1)


def db(sql):
    r = subprocess.run(MYSQL, input=sql, capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    if r.returncode != 0:
        raise RuntimeError("DB failed: " + (r.stderr or r.stdout or "")[:400] + " | SQL_HEAD=" + sql[:120])
    return r.stdout


def db_val(sql):
    out = db(sql).strip().split("\n")
    return out[0].split("\t")[0] if out and out[0] else ""


def uid_of(username):
    return int(db_val(f"SELECT id FROM sys_user WHERE username='{username}'"))


fails = []


def check(name, cond, detail=""):
    mark = "PASS" if cond else "FAIL"
    print(f"[{mark}] {name} {detail}")
    if not cond:
        fails.append(name)


def reg_login(username):
    phone = "13" + "".join(random.choice("0123456789") for _ in range(9))
    api("POST", "/auth/register", {"username": username, "password": PWD,
                                   "nickname": "vf", "phone": phone})
    la = api("POST", "/auth/login", {"username": username, "password": PWD})
    tok = (la.get("data") or {}).get("token")
    uid = int((la.get("data") or {}).get("user", {}).get("id"))
    return tok, uid


def make_addr(tok):
    a = api("POST", "/addresses", {"receiverName": "验证", "receiverPhone": "13800000000",
                                   "province": "北京市", "city": "北京市", "district": "朝阳区",
                                   "detailAddress": "验证地址", "isDefault": True}, tok)
    return (a.get("data") or {}).get("id")


def pick_products():
    prods = api("GET", "/products?size=100").get("data", {}).get("items", [])
    onsale = [p for p in prods if p.get("status") == "ON_SALE" and (p.get("stock") or 0) > 3]
    p1 = next((p for p in onsale if 50 <= (p.get("price") or 0) <= 200), None)
    phigh = max(onsale, key=lambda p: (p.get("price") or 0)) if onsale else None
    return p1, phigh


def pay_order(oid, tok):
    api("POST", f"/orders/{oid}/pay?channel=WECHAT", {}, tok)


def buy_paid(product, addr, tok, use_points=False, points_to_use=None):
    body = {"productId": product["id"], "quantity": 1, "skuSpec": None,
            "remark": "verify", "fulfillmentType": "EXPRESS", "addressId": addr}
    if use_points:
        body["usePoints"] = True
        if points_to_use is not None:
            body["pointsToUse"] = points_to_use
    res = api("POST", "/orders/quick-buy", body, tok)
    oid = (res.get("data") or {}).get("id")
    pay_order(oid, tok)
    return res.get("data", {})


print("========== 准备：临时管理员 + 商品 ==========")
adm_tok, adm_uid = reg_login(ADM)
db(f"UPDATE sys_user SET role='ADMIN' WHERE id={adm_uid}")
adm_tok = (api("POST", "/auth/login", {"username": ADM, "password": PWD}).get("data") or {}).get("token")
check("临时管理员拿到 ADMIN 令牌", bool(adm_tok))
p1, phigh = pick_products()
check("选到 P1 商品", p1 is not None, f"price={p1.get('price') if p1 else None}")
check("选到高价商品 P_high", phigh is not None, f"price={phigh.get('price') if phigh else None}")
POINTS_PER_YUAN = 100  # 与 MemberService.POINTS_PER_YUAN 一致

# ===================== 场景① C：申请即冻结，且冻结积分不可用于抵扣 =====================
print("\n========== 场景① C（申请即冻结 + 冻结分不可抵扣） ==========")
tok1, uid1 = reg_login(U1)
addr1 = make_addr(tok1)
a1 = buy_paid(p1, addr1, tok1)
oidA1 = a1.get("id")
payA1 = a1.get("payAmount")
prof1 = api("GET", "/member/profile", token=tok1).get("data", {})
pts1 = prof1.get("points")
print(f"订单A1 oid={oidA1} payAmount={payA1} 付款后积分={pts1}")
check("A1 付款后积分>0", (pts1 or 0) > 0, f"pts1={pts1}")

app1 = api("POST", "/returns", {"orderId": int(oidA1), "type": "REFUND", "reason": "场景① C 验证"}, tok1)
rid1 = (app1.get("data") or {}).get("id")
check("A1 退款申请提交", rid1 is not None)
prof1b = api("GET", "/member/profile", token=tok1).get("data", {})
frozen1 = prof1b.get("frozenPoints")
avail1 = prof1b.get("availablePoints")
print(f"申请后 frozen={frozen1} available={avail1}")
check("冻结=本次发放积分(pts1)", frozen1 == pts1, f"frozen={frozen1} expect={pts1}")
check("可用积分清零", avail1 == 0, f"avail={avail1}")
db_frozen1 = int(db_val(f"SELECT frozen_points FROM sys_user WHERE id={uid1}"))
check("DB frozen_points 一致", db_frozen1 == pts1, f"db_frozen={db_frozen1}")
db_rr_frozen1 = int(db_val(f"SELECT frozen_points FROM return_request WHERE id={rid1}"))
check("return_request.frozen_points 一致", db_rr_frozen1 == pts1, f"rr_frozen={db_rr_frozen1}")

# 尝试把冻结积分花在订单 B 上：请求用掉全部积分，断言实际抵扣为 0
b1 = api("POST", "/orders/quick-buy",
         {"productId": phigh["id"], "quantity": 1, "skuSpec": None, "usePoints": True,
          "pointsToUse": pts1, "remark": "场景① 试图花冻结分", "fulfillmentType": "EXPRESS",
          "addressId": addr1}, tok1)
oidB1 = (b1.get("data") or {}).get("id")
b1_used = (b1.get("data") or {}).get("pointsUsed") or 0
print(f"订单B1 oid={oidB1} 实际积分抵扣 pointsUsed={b1_used}")
check("冻结积分无法用于抵扣(pointsUsed==0)", b1_used == 0, f"used={b1_used}")
prof1c = api("GET", "/member/profile", token=tok1).get("data", {})
check("C 拦截后积分总额不变", prof1c.get("points") == pts1, f"pts={prof1c.get('points')} expect={pts1}")

# ===================== 场景② B：申请前已花掉 → 现金退款等价扣回 =====================
print("\n========== 场景② B（申请前花掉 → 现金扣回） ==========")
tok2, uid2 = reg_login(U2)
addr2 = make_addr(tok2)
a2 = buy_paid(p1, addr2, tok2)
oidA2 = a2.get("id")
payA2 = a2.get("payAmount")
pts2 = api("GET", "/member/profile", token=tok2).get("data", {}).get("points")
print(f"订单A2 oid={oidA2} payAmount={payA2} 付款后积分={pts2}")
check("A2 付款后积分>0", (pts2 or 0) > 0)
# 把 A2 的积分在申请前花掉（先用掉，制造套利前置条件）。
# 注意：故意不支付订单 B2，使其不产生「新发放积分」污染积分池，从而干净地验证「池为空 → 冻结=0 → 现金等价扣回」。
b2body = {"productId": phigh["id"], "quantity": 1, "skuSpec": None, "usePoints": True,
          "pointsToUse": pts2, "remark": "场景② 花掉A2积分(不付款)", "fulfillmentType": "EXPRESS",
          "addressId": addr2}
b2 = api("POST", "/orders/quick-buy", b2body, tok2)
oidB2 = (b2.get("data") or {}).get("id")
b2_used = (b2.get("data") or {}).get("pointsUsed") or 0
print(f"订单B2(未付款) oid={oidB2} 花掉积分 pointsUsed={b2_used}")
check("A2 积分已花掉(pointsUsed==pts2)", b2_used == pts2, f"used={b2_used} expect={pts2}")
# 申请退款（此时积分已花、池中无新发放积分，冻结额应为 0）
app2 = api("POST", "/returns", {"orderId": int(oidA2), "type": "REFUND", "reason": "场景② B 验证"}, tok2)
rid2 = (app2.get("data") or {}).get("id")
frozen2 = api("GET", "/member/profile", token=tok2).get("data", {}).get("frozenPoints")
check("申请前已花且池内无新积分 → 冻结额=0", frozen2 == 0, f"frozen={frozen2}")
# 管理员直接退款（PAID 免寄回）
rev2 = api("POST", f"/admin/return-requests/{rid2}/review", {"approved": True}, adm_tok)
rev2_data = rev2.get("data") or {}
print(f"审核 status={rev2_data.get('status')} refundAmount={rev2_data.get('refundAmount')} code={rev2.get('code')}")
# 核验：现金退款额 = 毛退款(payA2) − (应回收 − 实际回收)/100
gross2 = payA2
removed2 = 0
rows2 = [r for r in db(f"SELECT type,amount FROM point_ledger WHERE user_id={uid2} AND ref_order_id={oidA2}").strip().split("\n") if r]
for r in rows2:
    parts = r.split("\t")
    if len(parts) >= 2 and parts[0] == "EARN_REVERSE":
        removed2 += int(parts[1])
print(f"A2 EARN_REVERSE 实际回收积分={removed2}")
unrecovered = (pts2 or 0) - removed2
expected_deduction = unrecovered / POINTS_PER_YUAN   # 元
expected_cash = (gross2 or 0) - expected_deduction
actual_cash = rev2_data.get("refundAmount")
print(f"毛退款={gross2} 未回收积分={unrecovered} 预期扣回¥{expected_deduction:.2f} → 预期到账¥{expected_cash:.2f} 实际到账={actual_cash}")
check("现金退款扣回了已花积分等价金额", abs((actual_cash or 0) - expected_cash) < 0.011,
      f"actual={actual_cash} expect={expected_cash:.2f}")
# 钱包余额净变化：退款只加回 (payA2 − 已花积分等价) 而非全额
bal_end = api("GET", "/wallet", token=tok2).get("data", {}).get("balance")
print(f"U2 退款后钱包余额={bal_end}")

# ===================== 场景③ 驳回即释放冻结 =====================
print("\n========== 场景③ 驳回即释放冻结 =====================")
tok3, uid3 = reg_login(U3)
addr3 = make_addr(tok3)
a3 = buy_paid(p1, addr3, tok3)
oidA3 = a3.get("id")
pts3 = api("GET", "/member/profile", token=tok3).get("data", {}).get("points")
app3 = api("POST", "/returns", {"orderId": int(oidA3), "type": "REFUND", "reason": "场景③ 验证"}, tok3)
rid3 = (app3.get("data") or {}).get("id")
frozen3_pre = api("GET", "/member/profile", token=tok3).get("data", {}).get("frozenPoints")
check("申请时冻结=pts3", frozen3_pre == pts3, f"frozen={frozen3_pre} expect={pts3}")
# 管理员驳回
rej3 = api("POST", f"/admin/return-requests/{rid3}/review", {"approved": False, "remark": "测试驳回"}, adm_tok)
rej3_status = (rej3.get("data") or {}).get("status")
print(f"驳回 status={rej3_status}")
check("驳回态=REJECTED", rej3_status == "REJECTED", f"status={rej3_status}")
prof3 = api("GET", "/member/profile", token=tok3).get("data", {})
check("驳回后冻结清零", prof3.get("frozenPoints") == 0, f"frozen={prof3.get('frozenPoints')}")
check("驳回后积分余额复原", prof3.get("points") == pts3, f"pts={prof3.get('points')} expect={pts3}")
check("驳回后可用积分复原", prof3.get("availablePoints") == pts3, f"avail={prof3.get('availablePoints')} expect={pts3}")

# ===================== 清理 =====================
print("\n========== 清理 =====================")
try:
    uids = f"{uid1},{uid2},{uid3},{adm_uid}"
    for t in ("return_request", "order_item", "payment_record"):
        db(f"DELETE FROM {t} WHERE order_id IN (SELECT id FROM orders WHERE user_id IN ({uids}))")
    db(f"UPDATE stock_log SET order_id=NULL WHERE order_id IN (SELECT id FROM orders WHERE user_id IN ({uids}))")
    db(f"UPDATE stock_log SET operator_id=NULL WHERE operator_id IN ({uids})")
    db(f"UPDATE orders SET user_coupon_id=NULL WHERE user_id IN ({uids})")
    for t in ("product_review", "wallet_transaction", "user_message", "point_ledger",
              "user_favorite", "price_alert", "cart_item", "user_address", "user_coupon"):
        db(f"DELETE FROM {t} WHERE user_id IN ({uids})")
    db(f"DELETE FROM orders WHERE user_id IN ({uids})")
    db(f"DELETE FROM sys_user WHERE id IN ({uids})")
    print(f"清理完成：测试账号 {uids} 已删除")
except Exception as e:
    print(f"[WARN] 清理异常，请手工核查：{e}")

print("\n===== 结论 =====")
if fails:
    print("存在失败项: " + ", ".join(fails))
    sys.exit(1)
print("方案D（C+B）本地 E2E 全部通过 ✅")
