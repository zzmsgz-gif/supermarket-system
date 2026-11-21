#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
购买全链路 + 活动优惠口径覆盖测试（可重复跑，自建自清）。

覆盖：
  A 活动：无优惠基线 / 满减门槛边界 / 折扣活动 / 多活动取最优（两个方向）
  B 券：与活动互斥取大 / 券门槛不达 / 活动赢时券不消耗
  C 秒杀：剔除活动与券门槛 / 混合车 / 每人限购 / 秒杀价优先于会员价
  D 运费：快递<99 收 8 / >=99 免 / 自提免（按商品金额判，不按实付）
  E 积分：常规抵扣 / 上限夹取 / 三项叠加公式
  F 会员：普通用户原价 / 金卡会员价 vs 等级折扣取优
  G 多规格：同商品多规格行门槛累加
  H 落库：预览=下单 / 支付 / 取消（库存·券·积分回补）
所有写入记录前后快照，结束时回补库存、秒杀名额，删除测试账号与订单。

用法：
    python deploy/verify-promo-coverage.py            # 全量
    python deploy/verify-promo-coverage.py --keep     # 跑完不清理（调试用）
"""
import datetime
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

# 默认打线上；本地验证用 VERIFY_BASE=http://localhost:8080/api VERIFY_LOCAL=1
BASE = os.environ.get("VERIFY_BASE", "https://haomart.xyz/api")
LOCAL = os.environ.get("VERIFY_LOCAL") == "1"
SSH_HOST = "root@8.218.154.150"
DB = "supermarket_system"
LOCAL_MYSQL_PASS = "zzmsgz"
PY = sys.executable

# ---------- 基础设施 ----------
def http(method, path, token=None, body=None, timeout=30, retry=2):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    for attempt in range(retry + 1):
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Content-Type", "application/json")
        if token:
            req.add_header("Authorization", "Bearer " + token)
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                return resp.getcode(), json.loads(resp.read().decode() or "{}")
        except urllib.error.HTTPError as e:
            try:
                return e.code, json.loads(e.read().decode() or "{}")
            except Exception:
                return e.code, {}
        except Exception as e:
            if attempt == retry:
                raise
            time.sleep(1.5)
    return 0, {}


def sql_many(statements):
    """一次 ssh 执行多条 SQL，返回原始文本行。"""
    joined = "; ".join(s.strip().rstrip(";") for s in statements)
    import shlex
    if LOCAL:
        cmd = ["mysql", "-uroot", "-p" + LOCAL_MYSQL_PASS, "-N", "-B",
               "--default-character-set=utf8mb4", DB, "-e", joined]
    else:
        remote = ("mysql -uroot -N -B --default-character-set=utf8mb4 %s -e %s"
                  % (DB, shlex.quote(joined)))
        cmd = ["ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=25", SSH_HOST, remote]
    p = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    if p.returncode != 0 and p.stderr.strip():
        # SQL 出错必须显式暴露：静默返回空列表会把「查询失败」伪装成「没有数据」，
        # 排查时会误判成业务状态不对（J4 就是这样踩过一次）
        print("      ⚠ SQL 失败: %s | SQL=%s" % (p.stderr.strip()[:160], joined[:120]))
    out = p.stdout
    return [ln for ln in out.splitlines() if "store now" not in ln.lower()
            and "openssh" not in ln.lower() and "post-quantum" not in ln.lower()]


def sql_one(q):
    # 空结果重试一次：线上走 ssh，偶发抖动会返回空 stdout（returncode 仍是 0），
    # 如果不重试就会把「查询失败」误判成「状态不对」，排查时非常误导。
    for _ in range(2):
        rows = sql_many([q])
        if rows:
            return rows[0]
    return None


# ---------- 结果记录 ----------
RESULTS = []


def record(group, name, ok, detail):
    if ok is None:          # None = 跳过（本环境缺数据），既不算过也不算失败
        print("  ⊘ [%s] %s  (跳过：%s)" % (group, name, detail))
        return
    RESULTS.append((group, name, ok, detail))
    print("  %s [%s] %s" % ("✔" if ok else "✗", group, name))
    if not ok:
        print("        %s" % detail)
    elif detail:
        print("        %s" % detail)


def near(a, b, tol=0.011):
    try:
        return abs(float(a) - float(b)) <= tol
    except (TypeError, ValueError):
        return False


def check_preview(group, name, body, token, expect):
    """调 preview 并按 expect 断言。expect 里键为 preview 字段名。"""
    code, res = http("POST", "/orders/preview", token, body)
    data = res.get("data") or {}
    if code != 200 or not data:
        record(group, name, False, "HTTP %s %s" % (code, res.get("message")))
        return None
    bad = []
    for k, want in expect.items():
        got = data.get(k)
        if isinstance(want, str):
            if (got or "") != want:
                bad.append("%s: got %r want %r" % (k, got, want))
        else:
            if not near(got, want):
                bad.append("%s: got %r want %r" % (k, got, want))
    ok = not bad
    detail = "goods=%s 活动=%s 券=%s best=%s 积分=%s 运费=%s 应付=%s" % (
        data.get("goodsAmount"), data.get("activityDiscount"), data.get("couponDiscount"),
        data.get("bestPromo"), data.get("pointsDiscount"), data.get("freightAmount"),
        data.get("payAmount"))
    if bad:
        detail = "; ".join(bad) + " || " + detail
    record(group, name, ok, detail)
    return data


def new_user(tag, level=0, points=0, balance=None):
    name = "vfyb%s%d" % (tag, int(time.time()) % 10000)
    code, res = http("POST", "/auth/register", None, {
        "username": name, "password": "Bt123456",
        "phone": "139" + str(int(time.time() * 1000))[-8:],
        "nickname": "购买测试", "email": name + "@example.com",
    })
    if code != 200:
        raise SystemExit("注册失败 %s %s" % (code, res))
    code, res = http("POST", "/auth/login", None, {"username": name, "password": "Bt123456"})
    token = (res.get("data") or {}).get("token")
    if not token:
        raise SystemExit("登录失败 %s" % res)
    uid = sql_one("SELECT id FROM sys_user WHERE username='%s'" % name)
    stmts = ["UPDATE sys_user SET member_level=%d, points=%d, total_spent=0 WHERE id=%s" % (level, points, uid)]
    if balance is not None:
        stmts.append("UPDATE sys_user SET balance=%s WHERE id=%s" % (balance, uid))
    sql_many(stmts)
    return {"name": name, "token": token, "id": uid}


def receive_coupon(user, coupon_id):
    code, res = http("POST", "/coupons/%s/receive" % coupon_id, user["token"])
    return code == 200, res.get("message")


def my_coupon_id(user, coupon_id):
    return sql_one("SELECT uc.id FROM user_coupon uc WHERE uc.user_id=%s AND uc.coupon_id=%s "
                   "AND uc.status='UNUSED' ORDER BY uc.id DESC LIMIT 1" % (user["id"], coupon_id))


# ---------- 主流程 ----------
def main():
    keep = "--keep" in sys.argv
    t0 = time.time()
    print("=" * 78)
    print(" 购买 + 活动优惠口径覆盖测试  %s" % time.strftime("%Y-%m-%d %H:%M"))
    print("=" * 78)

    # 商品基准数据（脚本假设这些值，跑前先断言，数据被改就明确报出来而不是错判）
    base = sql_many([
        "SELECT CONCAT(id,'|',price,'|',stock,'|',sku,'|',status,'|',kind) FROM product WHERE id IN (2,3,95,173,187)",
        "SELECT CONCAT(product_id,'|',price) FROM product_sku WHERE product_id IN (2,3) AND deleted=0",
        "SELECT CONCAT('ACT|',id,'|',type,'|',IFNULL(threshold,0),'|',IFNULL(discount,0)) FROM activity WHERE status=1 AND deleted=0 AND start_time<=NOW() AND end_time>=NOW()",
        "SELECT CONCAT('CP|',id,'|',threshold_amount,'|',discount_amount,'|',status) FROM coupon WHERE id=37",
    ])
    print("  基准数据：")
    for ln in base:
        print("    " + ln)

    # 库存快照：跑完自检「测试只借用库存，清理后必须分毫回到原值」
    stock_snapshot = {}
    for _pid in (1, 2, 3, 95, 173, 187):
        _v = sql_one("SELECT stock FROM product WHERE id=%d" % _pid)
        if _v is not None:
            stock_snapshot[_pid] = _v

    u1 = new_user("a", level=0, points=5000, balance=1000)
    u2 = new_user("b", level=2, points=0, balance=0)
    print("  测试账号：%s(普通,5000分) %s(金卡)" % (u1["name"], u2["name"]))

    # 领券：满50减10
    ok, msg = receive_coupon(u1, 37)
    receive_coupon(u2, 37)
    uc1 = my_coupon_id(u1, 37)
    print("  领券 37：#%s (%s)" % (uc1, "成功" if ok else msg))

    T1, T2 = u1["token"], u2["token"]
    SKU_SP70 = "规格:70g"
    SKU_SP135 = "规格:135g"
    SKU_W350 = "规格:350ml"

    def pv(product_id, qty, sku=None, ful="PICKUP", token=T1, coupon=None, points=False, coupon_tag=None, addr=None):
        body = {"productId": product_id, "quantity": qty, "fulfillmentType": ful}
        if sku:
            body["skuSpec"] = sku
        if coupon:
            body["userCouponId"] = coupon
        if points:
            body["usePoints"] = True
        if addr:
            body["addressId"] = addr
        return body

    # ===== A 活动 =====
    print("\n--- A 活动优惠 ---")
    check_preview("A", "A1 无优惠商品 矿泉水350ml×2=5.00",
                  pv(2, 2, SKU_W350), T1,
                  {"goodsAmount": 5.00, "activityDiscount": 0, "couponDiscount": 0,
                   "promoType": "NONE", "freightAmount": 0, "payAmount": 5.00})

    check_preview("A", "A2 差一点未达门槛 薯片70g×28=193.20",
                  pv(3, 28, SKU_SP70), T1,
                  {"goodsAmount": 193.20, "activityDiscount": 0, "payAmount": 193.20})

    check_preview("A", "A3 刚好过门槛 薯片70g×29=200.10 → 满200减50",
                  pv(3, 29, SKU_SP70), T1,
                  {"goodsAmount": 200.10, "activityDiscount": 50.00,
                   "activityName": "全场满200减50", "promoType": "ACTIVITY", "payAmount": 150.10})

    check_preview("A", "A4 大额时折扣赢 薯片70g×60=414.00 → 8折减82.80>减50",
                  pv(3, 60, SKU_SP70), T1,
                  {"goodsAmount": 414.00, "activityDiscount": 82.80,
                   "activityName": "全场满200打8折", "payAmount": 331.20})

    check_preview("A", "A5 折扣活动也有门槛 薯片70g×10=69.00 → 8折不打",
                  pv(3, 10, SKU_SP70), T1,
                  {"goodsAmount": 69.00, "activityDiscount": 0, "payAmount": 69.00})

    # 多规格按商品累加走购物车（见 G 组）

    # ===== B 券 =====
    print("\n--- B 券与活动互斥 ---")
    check_preview("B", "B1 券赢 薯片70g×10=69.00 券减10>活动0",
                  pv(3, 10, SKU_SP70, coupon=uc1), T1,
                  {"goodsAmount": 69.00, "couponDiscount": 10.00, "activityDiscount": 0,
                   "bestPromo": 10.00, "promoType": "COUPON", "payAmount": 59.00})

    check_preview("B", "B2 活动赢 薯片70g×29=200.10 活动减50>券减10",
                  pv(3, 29, SKU_SP70, coupon=uc1), T1,
                  {"goodsAmount": 200.10, "couponDiscount": 10.00, "activityDiscount": 50.00,
                   "bestPromo": 50.00, "promoType": "ACTIVITY", "payAmount": 150.10})

    check_preview("B", "B3 券门槛不达 矿泉水×2=5.00 用满50券 → 券不可用",
                  pv(2, 2, SKU_W350, coupon=uc1), T1,
                  {"goodsAmount": 5.00, "couponDiscount": 0, "bestPromo": 0, "payAmount": 5.00})

    check_preview("B", "B4 券门槛刚好 薯片70g×8=55.20 满50券可用",
                  pv(3, 8, SKU_SP70, coupon=uc1), T1,
                  {"goodsAmount": 55.20, "couponDiscount": 10.00, "payAmount": 45.20})

    # ===== C 秒杀 =====
    print("\n--- C 秒杀（纯折扣通道）---")
    # 秒杀场次要「此刻正在进行」才有意义；本地库常年没有场次，缺数据就跳过而不是算失败
    has_cherry = sql_one("SELECT COUNT(*) FROM flash_sale WHERE product_id=173 AND deleted=0 "
                         "AND start_time<=NOW() AND end_time>=NOW()") not in (None, "", "0")
    has_bun = sql_one("SELECT COUNT(*) FROM flash_sale WHERE product_id=187 AND deleted=0 "
                      "AND start_time<=NOW() AND end_time>=NOW()") not in (None, "", "0")
    if not has_cherry:
        record("C", "C1/C2 秒杀车厘子（场次进行中）", None, "本库无进行中的 173 场次")
    else:
        check_preview("C", "C1 秒杀车厘子×1 → 139.00，活动与券都不作用",
                      pv(173, 1, coupon=uc1), T1,
                      {"goodsAmount": 139.00, "nonFlashAmount": 0, "activityDiscount": 0,
                       "couponDiscount": 0, "bestPromo": 0, "promoType": "NONE", "payAmount": 139.00})

        code, res = http("POST", "/orders/preview", T1, pv(173, 3))
        rejected = code != 200 or not (res.get("data") or {})
        record("C", "C2 秒杀车厘子×3 超限购2 → 应拒绝", rejected,
               "HTTP %s msg=%s" % (code, res.get("message")))

    if not has_bun:
        record("C", "C3 秒杀奶黄包", None, "本库无进行中的 187 场次")
    else:
        check_preview("C", "C3 秒杀奶黄包×2 → 13.90×2=27.80（源商品16.90）",
                      pv(187, 2), T1,
                      {"goodsAmount": 27.80, "activityDiscount": 0, "couponDiscount": 0,
                   "payAmount": 27.80})

    # 混合车（秒杀+普通）走购物车验门槛基数，见 G3

    # ===== D 运费 =====
    print("\n--- D 运费（按商品金额，99 免邮）---")
    check_preview("D", "D1 快递 薯片70g×5=34.50 → 运费8.00",
                  pv(3, 5, SKU_SP70, ful="EXPRESS"), T1,
                  {"goodsAmount": 34.50, "freightAmount": 8.00, "payAmount": 42.50})

    check_preview("D", "D2 快递 薯片70g×14=96.60 未满99 → 运费8.00",
                  pv(3, 14, SKU_SP70, ful="EXPRESS"), T1,
                  {"goodsAmount": 96.60, "freightAmount": 8.00, "payAmount": 104.60})

    check_preview("D", "D3 快递 薯片70g×15=103.50 满99 → 免邮",
                  pv(3, 15, SKU_SP70, ful="EXPRESS"), T1,
                  {"goodsAmount": 103.50, "freightAmount": 0, "payAmount": 103.50})

    check_preview("D", "D4 自提 34.50 → 无运费",
                  pv(3, 5, SKU_SP70, ful="PICKUP"), T1,
                  {"goodsAmount": 34.50, "freightAmount": 0, "payAmount": 34.50})

    # 运费按商品金额判：96.60 + 券减10 → 实付 86.60，但商品金额 96.60 < 99 仍收运费
    check_preview("D", "D5 免邮看商品金额不看实付：96.60+券10 → 仍收运费8",
                  pv(3, 14, SKU_SP70, ful="EXPRESS", coupon=uc1), T1,
                  {"goodsAmount": 96.60, "couponDiscount": 10.00, "freightAmount": 8.00,
                   "payAmount": 94.60})

    # ===== E 积分 =====
    print("\n--- E 积分抵扣（100分=1元，上限=优惠后金额×50%）---")
    check_preview("E", "E1 积分上限夹取：薯片70g×29 应付150.10 → 上限75.05，余额5000分只抵50",
                  pv(3, 29, SKU_SP70, points=True), T1,
                  {"goodsAmount": 200.10, "activityDiscount": 50.00,
                   "pointsUsed": 5000, "pointsDiscount": 50.00, "payAmount": 100.10})

    check_preview("E", "E2 积分+券+自提：薯片70g×10=69.00 券10 → 抵扣上限29.50，余额5000→抵29.50",
                  pv(3, 10, SKU_SP70, coupon=uc1, points=True), T1,
                  {"goodsAmount": 69.00, "couponDiscount": 10.00,
                   "pointsUsed": 2950, "pointsDiscount": 29.50, "payAmount": 29.50})

    check_preview("E", "E3 积分+运费：快递34.50 抵上限17.25 + 运费8",
                  pv(3, 5, SKU_SP70, ful="EXPRESS", points=True), T1,
                  {"goodsAmount": 34.50, "pointsUsed": 1725, "pointsDiscount": 17.25,
                   "freightAmount": 8.00, "payAmount": 25.25})

    # 积分是否被误扣（preview 无副作用）
    bal = sql_one("SELECT points FROM sys_user WHERE id=%s" % u1["id"])
    record("E", "E4 preview 不扣积分（余额应仍 5000）", str(bal) == "5000", "余额=%s" % bal)

    # ===== F 会员价 =====
    print("\n--- F 会员价 / 等级折扣 ---")
    # 普通用户：冻干咖啡 59.90
    check_preview("F", "F1 普通用户 冻干咖啡×1 → 59.90（无会员价）",
                  pv(95, 1, token=T1), T1,
                  {"goodsAmount": 59.90, "payAmount": 59.90})
    # 金卡(level=2, 95折)：会员价 55.90 vs 95折 56.905 → 取 55.90
    check_preview("F", "F2 金卡会员价 55.90 < 95折56.91 → 取会员价",
                  pv(95, 1, token=T2), T2,
                  {"goodsAmount": 55.90, "payAmount": 55.90})
    # 金卡买无会员价的薯片：6.90×0.95=6.555 → 6.56（HALF_UP）
    check_preview("F", "F3 金卡等级折扣 薯片70g×1 → 6.90×0.95=6.56",
                  pv(3, 1, SKU_SP70, token=T2), T2,
                  {"goodsAmount": 6.56, "payAmount": 6.56})
    # 秒杀价 vs 会员价：金卡买秒杀奶黄包 13.90，应仍是 13.90（秒杀价更低）
    if not has_bun:
        record("F", "F4 金卡 × 秒杀价 vs 会员折扣", None, "本库无进行中的 187 场次")
    else:
        check_preview("F", "F4 金卡买秒杀奶黄包×1 → 13.90（秒杀价低于会员折扣）",
                      pv(187, 1, token=T2), T2,
                      {"goodsAmount": 13.90, "payAmount": 13.90})

    # ===== G 购物车多规格 G 组 =====
    print("\n--- G 购物车：多规格合并 / 混合车 ---")
    # 清空购物车
    http("GET", "/cart", T1)
    code, cart = http("GET", "/cart", T1)
    for it in ((cart.get("data") or {}).get("items") or []):
        http("DELETE", "/cart/items/%s" % it.get("id"), T1)

    # 同商品两个规格：70g×15=103.50 + 135g×5=59.50 → 合计 163.00（未达200）
    http("POST", "/cart/items", T1, {"productId": 3, "quantity": 15, "skuSpec": SKU_SP70})
    http("POST", "/cart/items", T1, {"productId": 3, "quantity": 5, "skuSpec": SKU_SP135})
    code, cart = http("GET", "/cart", T1)
    cd = cart.get("data") or {}
    record("G", "G1 同商品多规格合计 163.00 未达门槛 → 活动优惠 0",
           near(cd.get("selectedAmount"), 163.00) and near(cd.get("activityDiscount"), 0),
           "selectedAmount=%s activityDiscount=%s" % (cd.get("selectedAmount"), cd.get("activityDiscount")))

    # 再加 70g×7=48.30 → 合计 211.30 → 活动减50（8折减42.26，取50）
    http("POST", "/cart/items", T1, {"productId": 3, "quantity": 7, "skuSpec": SKU_SP70})
    code, cart = http("GET", "/cart", T1)
    cd = cart.get("data") or {}
    record("G", "G2 加到 211.30 → 活动减50（8折只有42.26）",
           near(cd.get("selectedAmount"), 211.30) and near(cd.get("activityDiscount"), 50.00),
           "selectedAmount=%s activityDiscount=%s name=%s" % (
               cd.get("selectedAmount"), cd.get("activityDiscount"), cd.get("activityName")))

    # 混合车：秒杀车厘子×1(139) + 普通薯片（211.30）——只有场次在跑时秒杀行才成立
    if not has_cherry:
        record("G", "G3 秒杀+普通混合车：门槛只算非秒杀", None, "本库无进行中的 173 场次")
    else:
        http("POST", "/cart/items", T1, {"productId": 173, "quantity": 1})
        code, cart = http("GET", "/cart", T1)
        cd = cart.get("data") or {}
        record("G", "G3 秒杀+普通混合车：活动门槛只算非秒杀 211.30 → 减50",
               near(cd.get("activityDiscount"), 50.00) and near(cd.get("nonFlashSelectedAmount"), 211.30),
               "activityDiscount=%s nonFlash=%s selectedAmount=%s" % (
                   cd.get("activityDiscount"), cd.get("nonFlashSelectedAmount"), cd.get("selectedAmount")))

    # 下单（购物车，带券与积分）→ 核对落库
    print("\n--- H 真实下单 → 落库核对 ---")
    ids = [it.get("id") for it in (cd.get("items") or [])]
    code, order = http("POST", "/orders", T1, {
        "cartItemIds": ids, "fulfillmentType": "PICKUP", "pickupStoreId": 1,
        "userCouponId": uc1, "usePoints": True,
    })
    od = order.get("data") or {}
    if code != 200 or not od:
        record("H", "H1 购物车下单成功", False, "HTTP %s %s" % (code, order.get("message")))
        oid = None
    else:
        oid = od.get("id")
        total = float(od.get("totalAmount") or 0)
        act = float(od.get("activityDiscount") or 0)
        pts = float(od.get("pointsDiscount") or 0)
        pay = float(od.get("payAmount") or 0)
        expect_pay = round(total - max(float(od.get("discountAmount") or 0), act) - pts, 2)
        record("H", "H1 购物车下单：实付=商品−max(券,活动)−积分",
               near(pay, expect_pay) and near(total, float(cd.get("selectedAmount") or 0))
               and act > 0 and near(act, float(cd.get("activityDiscount") or 0)),
               "单号=%s 商品=%s 券=%s 活动=%s 积分抵扣=%s(%s分) 运费=%s 实付=%s 期望=%s" % (
                   od.get("orderNo"), total, od.get("discountAmount"), act, pts,
                   od.get("pointsUsed"), od.get("freightAmount"), pay, expect_pay))
        # 活动赢 → 券不应被消耗
        cst = sql_one("SELECT CONCAT(status,'|',IFNULL(order_id,'NULL')) FROM user_coupon WHERE id=%s" % uc1)
        record("H", "H2 活动赢时券未被消耗", cst.startswith("UNUSED"), "券=%s" % cst)
        # 积分真扣
        bal2 = sql_one("SELECT points FROM sys_user WHERE id=%s" % u1["id"])
        record("H", "H3 积分真实扣减 5000→%s" % bal2,
               str(bal2) == str(5000 - int(od.get("pointsUsed") or 0)), "余额=%s" % bal2)

    # ===== 支付 / 取消 / 回补 =====
    if oid:
        code, res = http("POST", "/orders/%s/pay" % oid, T1)
        pod = res.get("data") or {}
        record("H", "H4 余额支付成功", code == 200, "HTTP %s %s 实付=%s" % (
            code, res.get("message"), pod.get("payAmount")))
        flow = sql_one("SELECT CONCAT(type,'|',amount) FROM wallet_transaction WHERE order_id=%s" % oid)
        record("H", "H5 生成支付流水", bool(flow and flow.startswith("PAYMENT")), "流水=%s" % flow)

        # 取消已支付订单（全额退款）：库存/名额/券/积分/累计消费都应回到下单前
        code, res = http("POST", "/orders/%s/cancel" % oid, T1)
        record("H", "H6 取消已支付订单（原路退款）", code == 200,
               "HTTP %s %s" % (code, res.get("message")))
        time.sleep(1)
        st = sql_one("SELECT CONCAT(status,'|',IFNULL(payment_status,'-')) FROM orders WHERE id=%s" % oid)
        record("H", "H7 订单状态 CANCELED + 退款标记", st.startswith("CANCELED"), "订单=%s" % st)

        bal3 = sql_one("SELECT points FROM sys_user WHERE id=%s" % u1["id"])
        record("H", "H8 取消后积分回到下单前 5000（抵扣返还+发放回收）",
               near(bal3, 5000), "积分=%s" % bal3)

        spent = sql_one("SELECT total_spent FROM sys_user WHERE id=%s" % u1["id"])
        record("H", "H9 取消后累计消费回退", near(spent, 0), "total_spent=%s" % spent)

        lvl = sql_one("SELECT member_level FROM sys_user WHERE id=%s" % u1["id"])
        record("H", "H10 取消后会员等级不被支付抬高", str(lvl) == "0", "level=%s" % lvl)

        cst2 = sql_one("SELECT status FROM user_coupon WHERE id=%s" % uc1)
        record("H", "H11 本单活动赢、券未消耗 → 取消后仍可用", str(cst2) == "UNUSED", "券=%s" % cst2)

    # ===== I 已支付取消：券被核销的情况必须还券 =====
    print("\n--- I 已支付订单取消：核销过的券要还回来 ---")
    http("POST", "/cart/items", T1, {"productId": 3, "quantity": 10, "skuSpec": SKU_SP70})
    code, cart2 = http("GET", "/cart", T1)
    ids2 = [it.get("id") for it in ((cart2.get("data") or {}).get("items") or [])]
    code, o2 = http("POST", "/orders", T1, {
        "cartItemIds": ids2, "fulfillmentType": "PICKUP", "pickupStoreId": 1, "userCouponId": uc1,
    })
    o2d = o2.get("data") or {}
    oid2 = o2d.get("id")
    if code != 200 or not oid2:
        record("I", "I1 券生效单下单", False, "HTTP %s %s" % (code, o2.get("message")))
    else:
        used = sql_one("SELECT status FROM user_coupon WHERE id=%s" % uc1)
        record("I", "I1 券生效单：券被核销（USED）", str(used) == "USED",
               "券=%s 实付=%s" % (used, o2d.get("payAmount")))
        code, res = http("POST", "/orders/%s/pay" % oid2, T1)
        code, res = http("POST", "/orders/%s/cancel" % oid2, T1)
        record("I", "I2 支付后取消", code == 200, "HTTP %s %s" % (code, res.get("message")))
        time.sleep(1)
        back = sql_one("SELECT status FROM user_coupon WHERE id=%s" % uc1)
        record("I", "I3 取消后券应恢复可用（USED→UNUSED）", str(back) == "UNUSED", "券=%s" % back)
        spent2 = sql_one("SELECT CONCAT(points,'|',total_spent,'|',member_level) FROM sys_user WHERE id=%s" % u1["id"])
        record("I", "I4 两单都取消后积分=5000/消费=0/等级=0", str(spent2) == "5000|0.00|0",
               "points|spent|level=%s" % spent2)

    # ===== J 未支付取消 / 库存不足 / 超时自动关单 =====
    print("\n--- J 未支付取消 / 库存不足 / 超时自动关单 ---")

    def clear_cart(tok):
        code, c = http("GET", "/cart", tok)
        for it in ((c.get("data") or {}).get("items") or []):
            http("DELETE", "/cart/items/%s" % it.get("id"), tok)

    u3 = new_user("c", level=0, points=3000, balance=500)
    T3 = u3["token"]
    receive_coupon(u3, 37)
    uc3 = my_coupon_id(u3, 37)

    # J1/J2 未支付取消：库存 / 券 / 积分都要回来
    clear_cart(T3)
    stock0 = int(sql_one("SELECT stock FROM product WHERE id=3"))
    http("POST", "/cart/items", T3, {"productId": 3, "quantity": 10, "skuSpec": SKU_SP70})
    code, c3 = http("GET", "/cart", T3)
    ids3 = [it.get("id") for it in ((c3.get("data") or {}).get("items") or [])]
    code, o3 = http("POST", "/orders", T3, {
        "cartItemIds": ids3, "fulfillmentType": "PICKUP", "pickupStoreId": 1,
        "userCouponId": uc3, "usePoints": True})
    o3d = o3.get("data") or {}
    oid3 = o3d.get("id")
    if code != 200 or not oid3:
        record("J", "J1 未支付单建单", False, "HTTP %s %s" % (code, o3.get("message")))
    else:
        # 69.00 − 券10 − 积分29.50（上限 59×50%=29.5，2950 分）= 29.50
        stock1 = int(sql_one("SELECT stock FROM product WHERE id=3"))
        pts1 = sql_one("SELECT points FROM sys_user WHERE id=%s" % u3["id"])
        record("J", "J1 未支付单：券核销/积分抵扣/库存扣减",
               near(o3d.get("payAmount"), 29.50) and near(o3d.get("pointsUsed"), 2950)
               and stock1 == stock0 - 10 and str(pts1) == "50",
               "实付=%s 用分=%s 库存 %s→%s 积分=%s" % (
                   o3d.get("payAmount"), o3d.get("pointsUsed"), stock0, stock1, pts1))

        code, res = http("POST", "/orders/%s/cancel" % oid3, T3)
        time.sleep(1)
        stock2 = int(sql_one("SELECT stock FROM product WHERE id=3"))
        pts2 = sql_one("SELECT points FROM sys_user WHERE id=%s" % u3["id"])
        cst3 = sql_one("SELECT status FROM user_coupon WHERE id=%s" % uc3)
        record("J", "J2 未支付取消：库存/积分/券全部回补",
               code == 200 and stock2 == stock0 and str(pts2) == "3000" and str(cst3) == "UNUSED",
               "HTTP %s 库存=%s(应%s) 积分=%s(应3000) 券=%s" % (code, stock2, stock0, pts2, cst3))

    # J3 库存不足应拒绝
    code, res = http("POST", "/orders/preview", T3, pv(1, 999, "规格:5斤装", token=T3))
    record("J", "J3 库存不足应拒绝（红富士苹果 999 件）",
           code != 200 or not (res.get("data") or {}),
           "HTTP %s msg=%s" % (code, res.get("message")))

    # J4 超时未支付自动关单（15 分钟）：把 created_at 改到 16 分钟前，等定时任务（≤60s）
    clear_cart(T3)
    stock3 = int(sql_one("SELECT stock FROM product WHERE id=3"))
    http("POST", "/cart/items", T3, {"productId": 3, "quantity": 5, "skuSpec": SKU_SP70})
    code, c4 = http("GET", "/cart", T3)
    ids4 = [it.get("id") for it in ((c4.get("data") or {}).get("items") or [])]
    code, o4 = http("POST", "/orders", T3, {
        "cartItemIds": ids4, "fulfillmentType": "PICKUP", "pickupStoreId": 1,
        "userCouponId": uc3, "usePoints": True})
    o4d = o4.get("data") or {}
    oid4 = o4d.get("id")
    if code != 200 or not oid4:
        record("J", "J4 超时关单单建单", False, "HTTP %s %s" % (code, o4.get("message")))
    else:
        pts_before = sql_one("SELECT points FROM sys_user WHERE id=%s" % u3["id"])
        # 支付超时时长随环境而变（application.yml 15 分钟 / application-prod.yml 30 分钟），
        # 而且 OrderResponse 不保证带 createdAt，从 payDeadline 反推并不可靠。
        # 所以直接改到「足够早」（2 小时前）：任何合理的阈值都会命中，比精确推断稳得多。
        sql_many(["UPDATE orders SET created_at=DATE_SUB(NOW(), INTERVAL 120 MINUTE) WHERE id=%s" % oid4])
        print("    已把建单时间改到 2 小时前，等待关单任务（≤75s）…")
        closed = None
        for _ in range(15):
            time.sleep(5)
            st = sql_one("SELECT status FROM orders WHERE id=%s" % oid4)
            if st in ("CLOSED", "CANCELED"):
                closed = st
                break
        stock4 = int(sql_one("SELECT stock FROM product WHERE id=3"))
        pts_after = sql_one("SELECT points FROM sys_user WHERE id=%s" % u3["id"])
        cst4 = sql_one("SELECT status FROM user_coupon WHERE id=%s" % uc3)
        record("J", "J4 超时未支付自动关单", closed == "CLOSED", "状态=%s" % closed)
        record("J", "J5 超时关单：库存回补", stock4 == stock3, "库存=%s(应%s)" % (stock4, stock3))
        record("J", "J6 超时关单：券返还", str(cst4) == "UNUSED", "券=%s" % cst4)
        record("J", "J7 超时关单：抵扣积分必须退回（否则用户白丢）",
               str(pts_after) == "3000", "关单前=%s 关单后=%s(应3000)" % (pts_before, pts_after))

    # ===== 汇总 =====
    total = len(RESULTS)
    passed = sum(1 for r in RESULTS if r[2])
    print("\n" + "=" * 78)
    print(" 结果：%d/%d 通过" % (passed, total))
    fails = [r for r in RESULTS if not r[2]]
    if fails:
        print(" 失败项：")
        for g, n, _, d in fails:
            print("   ✗ [%s] %s | %s" % (g, n, d))
    print(" 耗时 %.0fs" % (time.time() - t0))

    # ===== 清理 =====
    if keep:
        print("\n --keep：跳过清理。手动清理账号：%s %s %s" % (u1["name"], u2["name"], u3["name"]))
        return 0 if not fails else 1

    print("\n--- 清理 ---")
    cleanup([u1, u2, u3])

    # 清理自检：下单→取消→超时关单→cleanup 一条链走完，库存必须净零变化。
    # 只要这里报漂移，就说明「某条回补路径重复了」，别急着走，先校准库。
    drift = []
    for _pid, _before in stock_snapshot.items():
        _now = sql_one("SELECT stock FROM product WHERE id=%d" % _pid)
        if str(_now) != str(_before):
            drift.append("商品%s: %s → %s" % (_pid, _before, _now))
    if drift:
        print("  ⚠️ 库存漂移，需人工校准: " + "; ".join(drift))
    else:
        print("  ✓ 库存自检通过：全部回到测试前数值")
    return 0 if not fails else 1


def cleanup(users):
    """删测试账号及其全部关联数据，并按订单明细回补库存与秒杀名额。"""
    ids = ",".join(u["id"] for u in users)
    # 下单明细（用于回补库存）
    # ⚠️ 只回补「还占着库存」的订单：已 CANCELED / CLOSED 的订单在取消、超时关单、退款时
    # 系统**已经**把库存与秒杀名额还回去了，这里再按 order_item 加一遍就是重复回补。
    # 实测教训：原先不判状态，多跑几轮把商品3 库存从 152 抬到 444（库存虚高 = 超卖风险），
    # 商品173 也从 96 抬到 103。
    rows = sql_many([
        "SELECT CONCAT(oi.product_id,'|',oi.quantity,'|',IFNULL(oi.flash_sale_id,0)) "
        "FROM order_item oi JOIN orders o ON o.id=oi.order_id "
        "WHERE o.user_id IN (%s) "
        "AND o.status IN ('PENDING_PAYMENT','PAID','SHIPPED','COMPLETED')" % ids,
    ])
    back = {}
    flash_back = {}
    for ln in rows:
        parts = ln.split("|")
        if len(parts) != 3:
            continue
        pid, qty, fsid = int(parts[0]), int(parts[1]), int(parts[2])
        back[pid] = back.get(pid, 0) + qty
        if fsid:
            flash_back[fsid] = flash_back.get(fsid, 0) + qty

    stmts = ["SET FOREIGN_KEY_CHECKS=0"]
    sub = ("SELECT id FROM orders WHERE user_id IN (%s)" % ids)
    for tbl, col in [("stock_log", "order_id"), ("payment_record", "order_id"),
                     ("return_request", "order_id"), ("wallet_transaction", "order_id"),
                     ("user_coupon", "order_id"), ("product_review", "order_id")]:
        stmts.append("DELETE FROM %s WHERE %s IN (%s)" % (tbl, col, sub))
    stmts.append("DELETE FROM order_item WHERE order_id IN (%s)" % sub)
    stmts.append("DELETE FROM orders WHERE user_id IN (%s)" % ids)
    for tbl in ["wallet_transaction", "point_ledger", "cart_item", "user_address",
                "user_message", "user_favorite", "price_alert", "user_coupon",
                "product_review", "recharge_order"]:
        stmts.append("DELETE FROM %s WHERE user_id IN (%s)" % (tbl, ids))
    # 领券计数按「本轮实际领到的张数」回退，别写死 2 —— 写死会让 received_count 每跑一轮就飘一点，
    # 多跑几轮后台看到的「已领取」就对不上了（本次实测从 121 飘到 125）
    cnt37 = int(sql_one("SELECT COUNT(*) FROM user_coupon WHERE user_id IN (%s) AND coupon_id=37" % ids) or 0)
    if cnt37:
        stmts.append("UPDATE coupon SET received_count=GREATEST(received_count-%d,0) WHERE id=37" % cnt37)
    stmts.append("DELETE FROM sys_user WHERE id IN (%s)" % ids)
    for pid, qty in back.items():
        stmts.append("UPDATE product SET stock=stock+%d, sales=GREATEST(sales-%d,0) WHERE id=%d"
                     % (qty, qty, pid))
    for fsid, qty in flash_back.items():
        stmts.append("UPDATE flash_sale SET sold_quota=GREATEST(sold_quota-%d,0) WHERE id=%d" % (qty, fsid))
    stmts.append("SET FOREIGN_KEY_CHECKS=1")
    sql_many(stmts)

    left = sql_many([
        "SELECT CONCAT('账号残留=', COUNT(*)) FROM sys_user WHERE id IN (%s)" % ids,
        "SELECT CONCAT('订单残留=', COUNT(*)) FROM orders WHERE user_id IN (%s)" % ids,
    ])
    print("  " + " ".join(left))
    print("  库存回补：%s" % (back or "无"))
    if flash_back:
        print("  秒杀名额回补：%s" % flash_back)


if __name__ == "__main__":
    sys.exit(main())
