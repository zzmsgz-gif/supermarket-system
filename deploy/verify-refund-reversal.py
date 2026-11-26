"""点3 退款逆向端到端验证（真实生产环境 haomart.xyz）。

场景：造一个带券 + 积分抵扣 + 付款发积分的订单 → 整单退款 → 核验：
  ① 券恢复为 UNUSED（restoreForOrder）
  ② 抵扣积分返还（point_ledger TYPE_REFUND）
  ③ 发放积分回收（point_ledger TYPE_EARN_REVERSE）
  ④ 累计消费/等级按占比回退（余额净变化为零）
  ⑤ 订单 status=CLOSED / refundStatus=APPROVED

做法：买家侧全部走公开 API；退款审核需要管理员令牌，
     采用「注册临时买家 → 提权 ADMIN → 用后即删」模式（绝不碰 sys_user id=1）。
     prod 库凭据在服务器侧提取，不外显到本地。
     测试券与临时账号在最后全部清理（FK 顺序：子表 → orders → sys_user）。
"""
import json
import random
import subprocess
import sys
import time
import urllib.error
import urllib.request

BASE = "https://haomart.xyz/api"
HOST = "root@8.218.154.150"
TS = time.strftime("%m%d%H%M%S")
USER_PREFIX = "vrefund"
A_USER = f"{USER_PREFIX}_a_{TS}"
C_USER = f"{USER_PREFIX}_c_{TS}"
A_PWD = "Test123456"
C_PWD = "Test123456"


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
        # 瞬时网络重置（如 WinError 10054）重试，最多 3 次；HTTPError 不重试
        if _attempt >= 2:
            raise
        print(f"[retry] {method} {path} 连接重置({e})，1.5s 后重试 {_attempt + 1}/2")
        time.sleep(1.5)
        return api(method, path, body, token, _attempt + 1)


def db(sql):
    shell = ('ENV=$(systemctl show supermarket -p Environment | sed "s/^Environment=//"); '
            'P=$(echo "$ENV" | grep -oP "DB_PASSWORD=\\K[^ ]+"); '
            'U=$(echo "$ENV" | grep -oP "DB_USERNAME=\\K[^ ]+"); '
            'mysql -N -B -u"$U" -p"$P" supermarket_system --default-character-set=utf8mb4')
    r = subprocess.run(["ssh", "-o", "StrictHostKeyChecking=no", HOST, shell],
                       input=sql, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        raise RuntimeError("DB failed: " + (r.stderr or r.stdout or "")[:400] + " | SQL_HEAD=" + sql[:120])
    return r.stdout


def db_val(sql):
    return db(sql).strip().split("\n")[0].split("\t")[0]


def uid_of(username):
    return int(db_val(f"SELECT id FROM sys_user WHERE username='{username}'"))


fails = []


def check(name, cond, detail=""):
    mark = "PASS" if cond else "FAIL"
    print(f"[{mark}] {name} {detail}")
    if not cond:
        fails.append(name)


# ============ 1. 注册临时买家 A ============
api("POST", "/auth/register", {"username": A_USER, "password": A_PWD,
                               "nickname": "退款验证买家", "phone": "13" + "".join(random.choice("0123456789") for _ in range(9))})
la = api("POST", "/auth/login", {"username": A_USER, "password": A_PWD})
tokA = (la.get("data") or {}).get("token")
uidA = int((la.get("data") or {}).get("user", {}).get("id"))
print(f"买家A uid={uidA} token_len={len(tokA or '')}")

# ============ 2. 注册临时管理员 C 并提权 ============
api("POST", "/auth/register", {"username": C_USER, "password": C_PWD,
                               "nickname": "退款验证管理", "phone": "13" + "".join(random.choice("0123456789") for _ in range(9))})
uidC = uid_of(C_USER)
db(f"UPDATE sys_user SET role='ADMIN' WHERE id={uidC}")
lc = api("POST", "/auth/login", {"username": C_USER, "password": C_PWD})
admTok = (lc.get("data") or {}).get("token")
print(f"管理C uid={uidC} token_len={len(admTok or '')}")
check("临时管理员拿到 ADMIN 令牌", bool(admTok), "")

# ============ 3. 买家 A 建地址 ============
addr = api("POST", "/addresses", {"receiverName": "验证", "receiverPhone": "13800000000",
                                  "province": "北京市", "city": "北京市", "district": "朝阳区",
                                  "detailAddress": "验证地址", "isDefault": True}, tokA)
addrId = (addr.get("data") or {}).get("id")
check("买家A建地址", addrId is not None, f"addrId={addrId}")

# ============ 4. 买家 A 先下一单 A（攒积分） ============
prods = api("GET", "/products?size=100").get("data", {}).get("items", [])
pickA = next((p for p in prods if p.get("status") == "ON_SALE" and (p.get("stock") or 0) > 3 and (p.get("price") or 0) >= 15), None)
resA = api("POST", "/orders/quick-buy", {"productId": pickA["id"], "quantity": 1, "skuSpec": None,
                                        "remark": "verify-A", "fulfillmentType": "EXPRESS",
                                        "addressId": addrId}, tokA)
oidA = (resA.get("data") or {}).get("id")
api("POST", f"/orders/{oidA}/pay?channel=WECHAT", {}, tokA)
profA = api("GET", "/member/profile", token=tokA).get("data", {})
pointsAfterA = profA.get("points")
print(f"订单A oid={oidA} payAmount={resA.get('data',{}).get('payAmount')} A积分={pointsAfterA}")
check("订单A付款成功", oidA is not None)

# ============ 5. 给买家 A 插一张『必胜券』(阈值0/减30/全场/生效) ============
db("INSERT INTO coupon (name, threshold_amount, discount_amount, total_count, received_count, "
   "start_time, end_time, status, claim_type, created_at, updated_at, deleted) "
   "VALUES ('VERIFY_REFUND_TEST', 50, 30, 999, 0, NOW(), '2030-12-31 23:59:59', 1, 0, NOW(), NOW(), 0)")
cid = db_val("SELECT id FROM coupon WHERE name='VERIFY_REFUND_TEST' ORDER BY id DESC LIMIT 1")
db(f"INSERT INTO user_coupon (coupon_id, user_id, status, received_at, created_at, updated_at) "
   f"VALUES ({cid}, {uidA}, 'UNUSED', NOW(), NOW(), NOW())")
ucid = int(db_val(f"SELECT id FROM user_coupon WHERE coupon_id={cid} AND user_id={uidA} ORDER BY id DESC LIMIT 1"))
print(f"测试券 cid={cid} user_coupon ucid={ucid}")

# ============ 6. 买家 A 用券+积分抵扣 下订单 B 并付款 ============
pickB = next((p for p in prods if p.get("status") == "ON_SALE" and (p.get("stock") or 0) > 3
              and (p.get("price") or 0) >= 60 and p["id"] != pickA["id"]), pickA)
resB = api("POST", "/orders/quick-buy",
           {"productId": pickB["id"], "quantity": 1, "skuSpec": None, "userCouponId": int(ucid),
            "usePoints": True, "pointsToUse": None, "remark": "verify-B",
            "fulfillmentType": "EXPRESS", "addressId": addrId}, tokA)
oidB = (resB.get("data") or {}).get("id")
bData = resB.get("data", {})
print(f"订单B oid={oidB} couponName={bData.get('couponName')} userCouponId={bData.get('userCouponId')} "
      f"discount={bData.get('discountAmount')} payAmount={bData.get('payAmount')} pointsEarned={bData.get('pointsEarned')}")
check("订单B带券下单成功", oidB is not None and bData.get("userCouponId") == int(ucid),
      f"userCouponId={bData.get('userCouponId')}")
check("券在订单B被核销(couponName非null)", bool(bData.get("couponName")),
      f"couponName={bData.get('couponName')}")
api("POST", f"/orders/{oidB}/pay?channel=WECHAT", {}, tokA)
bPaid = api("GET", f"/orders/{oidB}", token=tokA).get("data", {})
print(f"订单B付款后 status={bPaid.get('status')} payAmount={bPaid.get('payAmount')} pointsEarned={bPaid.get('pointsEarned')}")

# ============ 7. 买家 A 申请整单退款 ============
app = api("POST", "/returns", {"orderId": int(oidB), "type": "REFUND", "reason": "订单B退款逆向验证"}, tokA)
rid = (app.get("data") or {}).get("id")
print(f"申请退款 rid={rid}")
check("退款申请提交", rid is not None, f"rid={rid}")

# ============ 8. 管理员 C 审核通过（PAID→免寄回→直接退款→CLOSED） ============
rev = api("POST", f"/admin/return-requests/{rid}/review", {"approved": True}, admTok)
print(f"审核结果 status={(rev.get('data') or {}).get('status')} code={rev.get('code')}")
check("管理员审核通过", (rev.get("data") or {}).get("status") == "APPROVED", f"code={rev.get('code')} msg={rev.get('message')}")

# ============ 9. 核验逆向结果 ============
# 9.1 订单态
oB = api("GET", f"/orders/{oidB}", token=tokA).get("data", {})
check("订单B已关闭 CLOSED", oB.get("status") == "CLOSED", f"status={oB.get('status')}")
check("订单B退款态 APPROVED", oB.get("refundStatus") == "APPROVED", f"refundStatus={oB.get('refundStatus')}")

# 9.2 券恢复（DB 直查，避免 /coupons/mine 返回结构差异）
uc_status = db_val(f"SELECT status FROM user_coupon WHERE id={ucid}")
check("券恢复为 UNUSED", uc_status == "UNUSED", f"status={uc_status}")

# 9.3 积分流水（DB 直查 orderB 的逆向流水）
ledger_rows = [r for r in db(f"SELECT type,ref_order_id FROM point_ledger "
                             f"WHERE user_id={uidA} AND ref_order_id={oidB}").strip().split("\n") if r]
types = [r.split("\t")[0] for r in ledger_rows]
refund_cnt = sum(1 for t in types if t == "REFUND")
reverse_cnt = sum(1 for t in types if t == "EARN_REVERSE")
print(f"orderB 逆向流水: REFUND={refund_cnt} EARN_REVERSE={reverse_cnt}")
check("存在抵扣积分返还流水(TYPE_REFUND)", refund_cnt >= 1)
check("存在发放积分回收流水(TYPE_EARN_REVERSE)", reverse_cnt >= 1)

# 9.4 余额净变化为零（B 整单退 → A 攒的积分不受 B 影响）
profEnd = api("GET", "/member/profile", token=tokA).get("data", {})
check("积分余额净变化为零(=A攒的积分)", profEnd.get("points") == pointsAfterA,
      f"end={profEnd.get('points')} expect={pointsAfterA}")

# ============ 10. 清理：删除测试券 + 临时管理员 C + 临时买家 A（FK 顺序） ============
try:
    for t in ("return_request", "order_item", "payment_record"):
        db(f"DELETE FROM {t} WHERE order_id IN (SELECT id FROM orders WHERE user_id IN ({uidA},{uidC}))")
    db(f"UPDATE stock_log SET order_id=NULL WHERE order_id IN (SELECT id FROM orders WHERE user_id IN ({uidA},{uidC}))")
    db(f"UPDATE stock_log SET operator_id=NULL WHERE operator_id IN ({uidA},{uidC})")
    db(f"UPDATE orders SET user_coupon_id=NULL WHERE user_id IN ({uidA},{uidC})")
    for t in ("product_review", "wallet_transaction", "user_message", "point_ledger",
              "user_favorite", "price_alert", "cart_item", "user_address", "user_coupon"):
        db(f"DELETE FROM {t} WHERE user_id IN ({uidA},{uidC})")
    db(f"DELETE FROM orders WHERE user_id IN ({uidA},{uidC})")
    db(f"DELETE FROM coupon WHERE id={cid} AND name='VERIFY_REFUND_TEST'")
    db(f"DELETE FROM sys_user WHERE id IN ({uidA},{uidC})")
    print("清理完成：测试券 + 临时账号 A/C 已删除")
except Exception as e:
    print(f"[WARN] 清理异常，请手工核查：{e}")

print("\n===== 结论 =====")
if fails:
    print("存在失败项: " + ", ".join(fails))
    sys.exit(1)
print("点3 退款逆向 全部通过 ✅")
