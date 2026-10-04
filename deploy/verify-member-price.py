"""验证「会员价必须有身份门槛」+「逐商品取优、不叠加」。

对同一件会员价商品（id=89 月饼 原价128/会员价118）用不同等级账号立即购买，
核对成交单价：
  level0 普通用户 → 128.00（不享会员价，原价）
  level1 银卡     → 118.00（会员价；不再是「118 再打98折」的 115.64）
  level3 钻石     → 115.20（128×0.90 比会员价118更优 → 取等级折扣）
  level5 至尊     → 102.40（128×0.80，最高8折）
下单后立即取消以归还库存，再清理临时账号，做到零残留。
"""
import json
import sys
import time
import urllib.error
import urllib.request

sys.path.insert(0, "deploy")
from _db import run_sql  # noqa: E402

BASE = "http://localhost:8080/api"
PW = "Verify12345"
PRODUCT_ID = 89          # 中秋团圆月饼礼盒：price=128.00 member_price=118.00
_USER_ROWS = ("point_ledger", "wallet_transaction", "user_message", "user_favorite",
              "cart_item", "user_address", "user_coupon")


def req(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method,
                               headers={"Content-Type": "application/json"})
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=20) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}


def make_user(tag, level=None, spent=None):
    ts = int(time.time() * 1000)
    username = "vmp_%s_%d" % (tag, ts)
    phone = "13" + str(ts)[-9:].zfill(9)
    st, body = req("POST", "/auth/register",
                   {"username": username, "password": PW, "phone": phone, "nickname": tag})
    if body.get("code") != 0:
        raise SystemExit("注册失败 %s: %s" % (tag, body))
    uid = int(run_sql("SELECT id FROM sys_user WHERE username='%s'" % username).split()[0])
    if level is not None:
        run_sql("UPDATE sys_user SET member_level=%d, total_spent=%s WHERE id=%d" % (level, spent, uid))
    st, body = req("POST", "/auth/login", {"username": username, "password": PW})
    if body.get("code") != 0:
        raise SystemExit("登录失败 %s: %s" % (tag, body))
    return uid, body["data"]["token"]


def cleanup(uid):
    run_sql("UPDATE stock_log SET operator_id=NULL WHERE operator_id=%d" % uid)
    for t in _USER_ROWS:
        run_sql("DELETE FROM %s WHERE user_id=%d" % (t, uid))
    run_sql("DELETE FROM orders WHERE user_id=%d" % uid)
    run_sql("DELETE FROM sys_user WHERE id=%d" % uid)


def buy(token, store_id):
    """立即购买 1 件；返回 (orderId, 成交单价, 实付)"""
    st, body = req("POST", "/orders/quick-buy", {
        "productId": PRODUCT_ID, "quantity": 1,
        "fulfillmentType": "PICKUP", "pickupStoreId": store_id,
    }, token)
    if body.get("code") != 0:
        return None, None, None, "下单失败: %s" % body
    d = body["data"]
    unit = d["items"][0]["productPrice"]
    return d["id"], float(unit), float(d["payAmount"]), None


def main():
    store = run_sql("SELECT id FROM store WHERE status=1 LIMIT 1").split()
    store_id = int(store[0]) if store else None
    if not store_id:
        raise SystemExit("没有营业中的门店，无法自提下单")

    st, lv = req("GET", "/member/levels")
    print("== /member/levels (需登录) HTTP %s ==" % st)

    cases = [
        ("level0 普通用户", None, None, 128.00),
        ("level1 银卡", 1, "1500", 118.00),
        ("level3 钻石", 3, "25000", 115.20),
        ("level5 至尊", 5, "200000", 102.40),
    ]
    ok = True
    for tag, level, spent, expect in cases:
        uid, token = make_user(tag.split()[0], level, spent)
        st, lvbody = req("GET", "/member/levels", token=token)
        oid, unit, pay, err = buy(token, store_id)
        if err:
            print("%-16s %s" % (tag, err))
            ok = False
        else:
            good = abs(unit - expect) < 0.005
            ok = ok and good
            print("%-16s 成交单价=%7.2f  期望=%7.2f  实付=%7.2f  %s"
                  % (tag, unit, expect, pay, "OK" if good else "❌ 不符"))
            # 取消订单归还库存
            req("POST", "/orders/%d/cancel" % oid, {}, token)
        # 打印一次等级表（只打第一遍，避免刷屏）
        if tag.startswith("level0") and lvbody.get("code") == 0:
            print("  等级表：")
            for t in lvbody["data"]:
                print("    %s %-8s 门槛 %-9s 折扣 %s"
                      % (t["level"], t["name"], t["threshold"], t["rate"]))
        cleanup(uid)
    print("\n== 结论：%s ==" % ("全部通过" if ok else "存在不符，需排查"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
