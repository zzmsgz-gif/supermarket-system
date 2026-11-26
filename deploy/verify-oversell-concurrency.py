#!/usr/bin/env python3
"""超买超卖并发回归 —— 验证「同一份库存只扣一次、同一张订单只还一次」。

覆盖 2026-11-02 修复的四类并发缺口（都是真实能复现「库存虚高 → 超卖」的路径）：

  1. 并发抢最后 1 件     → 必须恰好 1 单成功，库存落到 0（不允许两单都成）
  2. 同一订单重复支付     → 只能成功 1 笔，payment_record 只有 1 条（不允许重复扣款）
  3. 同一订单并发取消     → 只能成功 1 笔，库存只回补 1 次（回两次就是库存虚高）
  4. 同一售后并发审批     → 只能放款 1 次，库存只回补 1 次

前置：本地后端跑在 localhost:8080，库由 _db 解析（application.yml / 环境变量）。
用法（deploy 目录下）： python verify-oversell-concurrency.py
自建自清：临时商品 / 临时账号 / 订单 / 售后单 / 流水全部删除。
"""
import sys
import threading
import time

from _admin_token import _req, _sql, temp_admin

BASE = "http://localhost:8080/api"
PASSWORD = "TmpUser12345"
STAMP = str(int(time.time()))
PASS, FAIL = [], []


def check(label, ok, detail=""):
    (PASS if ok else FAIL).append(label)
    print(("PASS" if ok else "FAIL"), "-", label, "::", detail)


def sql1(stmt):
    out = (_sql(stmt) or "").strip()
    return out.split()[0] if out else None


def ok_status(st):
    return isinstance(st, int) and st in (200, 201)


def concurrent(calls):
    """用 Barrier 对齐，让所有请求尽量同一瞬间发出。"""
    n = len(calls)
    barrier = threading.Barrier(n)
    results = [None] * n

    def wrap(i, fn):
        barrier.wait()
        try:
            results[i] = fn()
        except Exception as exc:      # noqa: BLE001
            results[i] = ("EXC", str(exc))

    threads = [threading.Thread(target=wrap, args=(i, fn)) for i, fn in enumerate(calls)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    return results


def statuses(res):
    return [r[0] if isinstance(r, tuple) else r for r in res]


def register(tag, seq):
    username = f"vfyos{tag}_{STAMP}"
    phone = "13" + str(int(STAMP[-5:]) * 10 + seq).zfill(9)[-9:]
    st, body = _req("POST", "/auth/register", {
        "username": username, "password": PASSWORD, "phone": phone, "nickname": f"并发{tag}",
    })
    if not ok_status(st) or body.get("code") != 0:
        raise SystemExit(f"注册临时用户失败 {username}: HTTP {st} {body}")
    st, body = _req("POST", "/auth/login", {"username": username, "password": PASSWORD})
    token = (body.get("data") or {}).get("token")
    if not token:
        raise SystemExit(f"登录失败 {username}: {body}")
    uid = int(sql1(f"SELECT id FROM sys_user WHERE username='{username}'"))
    return {"username": username, "uid": uid, "token": token}


def cleanup(uids, pid, sku):
    """按外键顺序清理（orders 反向引用 user_coupon，必须先把那一列置 NULL）。"""
    for uid in [u for u in uids if u]:
        try:
            _sql(f"DELETE FROM return_request WHERE user_id={uid}")
            _sql(f"DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE user_id={uid})")
            _sql(f"DELETE FROM payment_record WHERE order_id IN (SELECT id FROM orders WHERE user_id={uid})")
            _sql(f"UPDATE orders SET user_coupon_id=NULL WHERE user_id={uid}")
            _sql(f"UPDATE stock_log SET order_id=NULL, operator_id=NULL WHERE operator_id={uid}")
            for t in ("product_review", "wallet_transaction", "user_message", "point_ledger",
                      "user_favorite", "price_alert", "cart_item", "user_address", "user_coupon"):
                _sql(f"DELETE FROM {t} WHERE user_id={uid}")
            _sql(f"DELETE FROM orders WHERE user_id={uid}")
            _sql(f"DELETE FROM sys_user WHERE id={uid}")
        except Exception as exc:      # noqa: BLE001
            print(f"[cleanup] ⚠️ 用户 {uid} 清理失败：{exc}")
    try:
        if pid:
            _sql(f"DELETE FROM stock_log WHERE product_id={pid}")
            _sql(f"DELETE FROM order_item WHERE product_id={pid}")
            _sql(f"DELETE FROM product WHERE id={pid}")
    except Exception as exc:          # noqa: BLE001
        print(f"[cleanup] ⚠️ 商品 {pid} 清理失败：{exc}")


def main():
    adm = temp_admin("并发管理员")
    uids, pid = [], None
    try:
        cat_id = int(sql1("SELECT id FROM product_category ORDER BY id LIMIT 1"))
        store_id = int(sql1("SELECT id FROM store WHERE status=1 ORDER BY id LIMIT 1"))
        check("取到分类与自提门店", cat_id > 0 and store_id > 0, f"cat={cat_id} store={store_id}")

        # ---------- 造一件「只剩 1 件」的商品 ----------
        sku = f"VFYOS{STAMP}"
        _sql("INSERT INTO product (category_id, sku, name, price, stock, unit, status, deleted) "
             f"VALUES ({cat_id}, '{sku}', 'VFYOS并发测试品{STAMP}', 10.00, 1, '份', 'ON_SALE', 0)")
        pid = int(sql1(f"SELECT id FROM product WHERE sku='{sku}'"))
        check("造出 stock=1 的测试商品", pid > 0, f"productId={pid}")

        ua = register("a", 1)
        ub = register("b", 2)
        uids = [ua["uid"], ub["uid"]]
        check("注册两个独立买家", ua["uid"] != ub["uid"], f"uids={uids}")

        def quick_buy(user):
            return _req("POST", "/orders/quick-buy", {
                "productId": pid, "quantity": 1,
                "fulfillmentType": "PICKUP", "pickupStoreId": store_id,
            }, user["token"])

        # ---------- 场景 1：两人同时抢最后一件 ----------
        res = concurrent([lambda: quick_buy(ua), lambda: quick_buy(ub)])
        winners = [i for i, r in enumerate(res) if ok_status(r[0])]
        check("并发抢最后 1 件：恰好 1 单成功", len(winners) == 1, f"statuses={statuses(res)}")
        check("另一单被 409 拒绝（不是 500/双成）", statuses(res).count(409) == 1, f"statuses={statuses(res)}")
        stock = int(sql1(f"SELECT stock FROM product WHERE id={pid}"))
        check("库存扣到 0，未被超卖", stock == 0, f"stock={stock}")
        if len(winners) != 1:
            print("✗ 并发下单未达成预期，后续场景跳过")
            return finish()

        winner = (ua, ub)[winners[0]]
        order_id = res[winners[0]][1]["data"]["id"]

        # ---------- 场景 2：同一订单并发支付两次 ----------
        pays = concurrent([
            lambda: _req("POST", f"/orders/{order_id}/pay?channel=WECHAT", None, winner["token"]),
            lambda: _req("POST", f"/orders/{order_id}/pay?channel=WECHAT", None, winner["token"]),
        ])
        pay_msgs = [(p[0], (p[1] or {}).get("message") if isinstance(p[1], dict) else p[1]) for p in pays]
        check("并发重复支付：只成功 1 笔", statuses(pays).count(200) + statuses(pays).count(201) == 1,
              f"results={pay_msgs}")
        recs = int(sql1(f"SELECT count(*) FROM payment_record WHERE order_id={order_id}"))
        check("支付流水只有 1 条", recs == 1, f"payment_record={recs}")
        st = sql1(f"SELECT status FROM orders WHERE id={order_id}")
        check("订单终态为 PAID（不会被并发改花）", st == "PAID", f"status={st}")

        # ---------- 场景 3：同一订单并发取消两次 ----------
        cancels = concurrent([
            lambda: _req("POST", f"/orders/{order_id}/cancel", None, winner["token"]),
            lambda: _req("POST", f"/orders/{order_id}/cancel", None, winner["token"]),
        ])
        check("并发取消：只成功 1 笔", statuses(cancels).count(200) + statuses(cancels).count(201) == 1,
              f"statuses={statuses(cancels)}")
        stock = int(sql1(f"SELECT stock FROM product WHERE id={pid}"))
        check("库存只回补 1 次（回到 1，不是 2）", stock == 1, f"stock={stock}")
        st = sql1(f"SELECT status FROM orders WHERE id={order_id}")
        check("订单终态为 CANCELED", st == "CANCELED", f"status={st}")

        # ---------- 场景 4：并发审批同一笔售后 ----------
        st, body = quick_buy(ub)
        check("重新下单用于售后场景", ok_status(st), f"status={st} {body.get('message','')}")
        if not ok_status(st):
            return finish()
        order2 = body["data"]["id"]
        _req("POST", f"/orders/{order2}/pay?channel=WECHAT", None, ub["token"])
        item_id = int(sql1(f"SELECT id FROM order_item WHERE order_id={order2} ORDER BY id LIMIT 1"))
        st, body = _req("POST", "/returns", {
            "orderId": order2, "orderItemId": item_id,
            "reason": "并发审批测试", "type": "REFUND",
        }, ub["token"])
        rid = (body.get("data") or {}).get("id")
        check("提交售后申请", ok_status(st) and rid, f"status={st} msg={body.get('message','')}")
        if not rid:
            return finish()

        apprs = concurrent([
            lambda: _req("POST", f"/admin/return-requests/{rid}/review",
                         {"approved": True, "remark": "并发测试", "needReturn": False}, adm.token),
            lambda: _req("POST", f"/admin/return-requests/{rid}/review",
                         {"approved": True, "remark": "并发测试", "needReturn": False}, adm.token),
        ])
        check("并发审批同一售后：只成功 1 次", statuses(apprs).count(200) + statuses(apprs).count(201) == 1,
              f"statuses={statuses(apprs)}")
        stock = int(sql1(f"SELECT stock FROM product WHERE id={pid}"))
        check("售后回补库存只发生 1 次（stock=1，不是 2）", stock == 1, f"stock={stock}")
        refunds = sql1(f"SELECT count(*) FROM wallet_transaction WHERE order_id={order2} AND type='REFUND'")
        check("退款流水只有 1 条", refunds in ("1", None), f"refund_rows={refunds}")

        # ---------- 场景 5：支付与取消同时点（用户卡在超时边界） ----------
        # 说明：库存此时是 1（售后刚回补），再买 1 件应扣到 0。
        st, body = quick_buy(ua)
        if not ok_status(st):
            check("场景5 准备下单", False, f"status={st} {body.get('message','')}")
            return finish()
        order3 = body["data"]["id"]
        race = concurrent([
            lambda: _req("POST", f"/orders/{order3}/pay?channel=WECHAT", None, ua["token"]),
            lambda: _req("POST", f"/orders/{order3}/cancel", None, ua["token"]),
        ])
        won = [i for i, r in enumerate(race) if ok_status(r[0])]
        check("支付与取消并发：只有一方生效", len(won) == 1, f"statuses={statuses(race)}")
        st3 = sql1(f"SELECT status FROM orders WHERE id={order3}")
        stock3 = int(sql1(f"SELECT stock FROM product WHERE id={pid}"))
        # 支付赢 → 订单 PAID、库存保持已扣（0）；取消赢 → 订单 CANCELED、库存回补（1）。
        # 关键断言是「状态与库存必须自洽」：绝不允许出现「钱收了但库存还回去了」。
        expect = ("PAID", 0) if won and won[0] == 0 else ("CANCELED", 1)
        check("支付/取消后订单状态与库存自洽", (st3, stock3) == expect,
              f"winner={'pay' if won and won[0] == 0 else 'cancel'} status={st3} stock={stock3}")

    finally:
        cleanup(uids, pid, None)


def finish():
    print("\n=== 汇总 ===")
    print(f"通过 {len(PASS)} / 失败 {len(FAIL)}")
    if FAIL:
        print("失败项：", FAIL)
    sys.exit(1 if FAIL else 0)


if __name__ == "__main__":
    main()
    finish()
