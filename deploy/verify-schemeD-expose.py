"""方案D（C+B）用户可见性 端到端验证（真实生产 haomart.xyz）。

本次新增/改动：
  C：申请退款即冻结该单发放积分 → 接口返回 frozenPoints，前端「我的售后/积分页」可见。
  B：冻结不足（申请前已花）部分按 1积分=1/100元 从现金退款等价扣回 → 接口返回 pointsDeducted，
     前端金额明细「已花积分等价扣回」三行展示。

两个场景：
  场景① C（无缺口）：新号 → 下单O1付款攒47分 → 申请整单退款 → frozenPoints==47（全额冻结），
                        管理员退款 → pointsDeducted==0（没花过，无扣回）。
  场景② B（有缺口）：新号 → 下单O1付款攒47分 → 模拟「申请前已花掉42分」（DB 把可用积分降到5，
                        复现用户先花后退的真实套利场景）→ 申请退款 → frozenPoints==5（只冻住没花的），
                        管理员退款 → pointsDeducted==42（已花的等价现金扣回），实退 = 47.9 - 0.42。

做法：买家走公开 API；退款审核走「注册临时买家→提权 ADMIN→用后即删」。
      测试数据最后全部清理（FK 顺序：子表 → orders → sys_user）。
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
A_USER = f"vschd_a_{TS}"
C_USER = f"vschd_c_{TS}"
A_PWD = "Test123456"
C_PWD = "Test123456"

fails = []


def check(name, cond, detail=""):
    mark = "PASS" if cond else "FAIL"
    print(f"[{mark}] {name} {detail}")
    if not cond:
        fails.append(name)


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


def pick_product(price_min):
    prods = api("GET", "/products?size=100").get("data", {}).get("items", [])
    p = next((x for x in prods if x.get("status") == "ON_SALE" and (x.get("kind") or "NORMAL") != "FLASH"
              and (x.get("stock") or 0) > 3 and (x.get("price") or 0) >= price_min), None)
    return p


_buyer_seq = [0]


def setup_buyer(tag):
    _buyer_seq[0] += 1
    uname = f"vschd_{tag}_{TS}_{_buyer_seq[0]}"
    api("POST", "/auth/register", {"username": uname, "password": A_PWD,
                                   "nickname": "方案D买家", "phone": "13" + "".join(random.choice("0123456789") for _ in range(9))})
    time.sleep(1)
    la = api("POST", "/auth/login", {"username": uname, "password": A_PWD})
    tok = (la.get("data") or {}).get("token")
    uid = (la.get("data") or {}).get("user", {}).get("id")
    if not uid:
        print("[FATAL] 买家登录未拿到 id：", json.dumps(la, ensure_ascii=False)[:300])
        sys.exit(2)
    uid = int(uid)
    addr = api("POST", "/addresses", {"receiverName": "验证", "receiverPhone": "13800000000",
                                      "province": "北京市", "city": "北京市", "district": "朝阳区",
                                      "detailAddress": "验证地址", "isDefault": True}, tok)
    addrId = (addr.get("data") or {}).get("id")
    return uid, tok, addrId


def teardown(uids):
    try:
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
        print("清理完成：临时账号及其订单/售后已删除")
    except Exception as e:
        print(f"[WARN] 清理异常，请手工核查：{e}")


# ===== 注册管理员（两个场景共用） =====
api("POST", "/auth/register", {"username": C_USER, "password": C_PWD,
                               "nickname": "方案D管理", "phone": "13" + "".join(random.choice("0123456789") for _ in range(9))})
time.sleep(1)
uidC = uid_of(C_USER)
db(f"UPDATE sys_user SET role='ADMIN' WHERE id={uidC}")
admTok = (api("POST", "/auth/login", {"username": C_USER, "password": C_PWD}).get("data") or {}).get("token")
check("临时管理员拿到 ADMIN 令牌", bool(admTok))
all_uids = [uidC]

# ============ 场景① C（无缺口） ============
print("\n===== 场景① C：申请即冻结（无缺口） =====")
uidA, tokA, addrId = setup_buyer("c"); all_uids.append(uidA)
pick1 = pick_product(20)
res1 = api("POST", "/orders/quick-buy", {"productId": pick1["id"], "quantity": 1, "skuSpec": None,
                                         "remark": "vschd-O1", "fulfillmentType": "EXPRESS", "addressId": addrId}, tokA)
oid1 = (res1.get("data") or {}).get("id")
api("POST", f"/orders/{oid1}/pay?channel=WECHAT", {}, tokA)
prof1 = api("GET", "/member/profile", token=tokA).get("data", {})
P1 = prof1.get("points")
print(f"O1 oid={oid1} payAmount={res1.get('data',{}).get('payAmount')} 攒积分 P1={P1}")
check("O1付款后发放积分>0", (P1 or 0) > 0, f"P1={P1}")

app = api("POST", "/returns", {"orderId": int(oid1), "type": "REFUND", "reason": "方案D-C验证"}, tokA)
rid = (app.get("data") or {}).get("id")
appD = app.get("data") or {}
print(f"申请退款 rid={rid} frozenPoints={appD.get('frozenPoints')} pointsDeducted={appD.get('pointsDeducted')}")
check("C: 申请即冻结全部发放积分(frozenPoints==P1)", appD.get("frozenPoints") == P1,
      f"frozenPoints={appD.get('frozenPoints')} P1={P1}")
check("C: 申请态 pointsDeducted 恒0", appD.get("pointsDeducted") == 0)
rev = api("POST", f"/admin/return-requests/{rid}/review", {"approved": True}, admTok)
revD = rev.get("data") or {}
print(f"审核退款 status={revD.get('status')} refundAmount={revD.get('refundAmount')} pointsDeducted={revD.get('pointsDeducted')}")
check("管理员审核通过", revD.get("status") == "APPROVED", f"code={rev.get('code')} msg={rev.get('message')}")
check("C: 无缺口→pointsDeducted==0", (revD.get("pointsDeducted") or 0) == 0,
      f"pointsDeducted={revD.get('pointsDeducted')}")
check("C: 实退==应退", abs((revD.get("refundAmount") or 0) - (res1.get('data', {}).get('payAmount') or 0)) < 0.011,
      f"refundAmount={revD.get('refundAmount')}")

# ============ 场景② B（有缺口：模拟申请前已花掉部分积分） ============
print("\n===== 场景② B：冻结不足→现金等价扣回（有缺口） =====")
uidB, tokB, addrB = setup_buyer("b"); all_uids.append(uidB)
pick2 = pick_product(20)
res2 = api("POST", "/orders/quick-buy", {"productId": pick2["id"], "quantity": 1, "skuSpec": None,
                                         "remark": "vschd-B1", "fulfillmentType": "EXPRESS", "addressId": addrB}, tokB)
oidB = (res2.get("data") or {}).get("id")
api("POST", f"/orders/{oidB}/pay?channel=WECHAT", {}, tokB)
profB = api("GET", "/member/profile", token=tokB).get("data", {})
PB = profB.get("points")
print(f"O_B1 oid={oidB} 攒积分 PB={PB}")
# 模拟「申请退款前已把 42 分花掉」：把可用积分直接降到 5（复现实战中先花后退的套利场景，
# 此时冻结只能冻住没花掉的 5 分，剩下 42 分由方案D-B 从现金退款等价扣回）
db(f"UPDATE sys_user SET points=5, frozen_points=0 WHERE id={uidB}")
profB2 = api("GET", "/member/profile", token=tokB).get("data", {})
print(f"模拟花掉后 可用积分 available={profB2.get('availablePoints')} (PB={PB})")
check("模拟：可用积分已降到<发放额", (profB2.get("availablePoints") or 0) < PB,
      f"available={profB2.get('availablePoints')}")

app2 = api("POST", "/returns", {"orderId": int(oidB), "type": "REFUND", "reason": "方案D-B验证"}, tokB)
rid2 = (app2.get("data") or {}).get("id")
app2D = app2.get("data") or {}
print(f"申请退款 rid={rid2} frozenPoints={app2D.get('frozenPoints')} (应=available=5)")
check("B: 只冻结没花掉的积分(frozenPoints==available)", app2D.get("frozenPoints") == profB2.get("availablePoints"),
      f"frozenPoints={app2D.get('frozenPoints')} available={profB2.get('availablePoints')}")
rev2 = api("POST", f"/admin/return-requests/{rid2}/review", {"approved": True}, admTok)
rev2D = rev2.get("data") or {}
short = rev2D.get("pointsDeducted")
print(f"审核退款 status={rev2D.get('status')} refundAmount={rev2D.get('refundAmount')} pointsDeducted={short}")
check("管理员审核通过", rev2D.get("status") == "APPROVED", f"code={rev2.get('code')} msg={rev2.get('message')}")
check("B: 已花的积分等价扣回(pointsDeducted>0)", (short or 0) > 0, f"pointsDeducted={short}")
expected_refund = (res2.get('data', {}).get('payAmount') or 0) - (short or 0) / 100.0
check("B: 实退=应退-已花积分等价扣回", abs((rev2D.get('refundAmount') or 0) - expected_refund) < 0.011,
      f"refundAmount={rev2D.get('refundAmount')} expected={expected_refund}")

# ============ 清理 ============
teardown(",".join(str(u) for u in all_uids))

print("\n===== 结论 =====")
if fails:
    print("存在失败项: " + ", ".join(fails))
    sys.exit(1)
print("方案D 用户可见性（C冻结 + B扣回）端到端全部通过 ✅")
