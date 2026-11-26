#!/usr/bin/env python3
"""秒杀「每人限购」并发回归：同账号猛点只能成功 perUserLimit 件，不同用户并发不受影响。

背景（2026-11-02 生产实测由用户发现）：帝王蟹秒杀 perUserLimit=1，
同一账号 25 并发 quick-buy 抢到 4 件（订单 99287-99290），soldQuota 0→4。
名额扣减本身是原子的（没击穿 totalQuota），但限购判断被绕过。

根因：MySQL 默认 REPEATABLE READ。reserveQuota 里先 `SELECT ... FOR UPDATE` 锁场次行做串行化，
但紧接着的「该用户已买几件」是**普通 SELECT = 快照读**，读的是事务开始时的快照 ——
即使第二笔等到第一笔提交、拿到了锁，它的快照里依然看不到第一笔刚插入的 order_item，
于是判定「已购 0」放行。锁在、串行化在，但它读的是旧数据。
（修法：限购统计改成带 FOR UPDATE 的当前读，见 OrderItemRepository.sumFlashQuantityByUserForUpdate）

两个场景：
  1) 同账号 25 并发、perUserLimit=1 → 恰好 1 单成功，soldQuota=1，库存 49
  2) 3 个不同用户并发各买 1 件 → 全部成功，soldQuota 累计 4，库存 46（确认没「修过头」）

用法（deploy 下）： python verify-flash-peruser-race.py
自建自清：临时秒杀场次 + 临时账号 + 订单全部清理。
"""
import json
import sys
import threading
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta

from _admin_token import _sql, temp_admin

BASE = "http://localhost:8080/api"
PASSWORD = "TmpUser12345"
STAMP = str(int(time.time()))
CONCURRENCY = 25
PER_USER_LIMIT = 1
TOTAL_QUOTA = 50
PASS, FAIL = [], []


def req(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method,
                               headers={"Content-Type": "application/json"})
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:               # noqa: BLE001
            return e.code, {}
    except Exception as exc:            # noqa: BLE001
        return "EXC", str(exc)


def check(label, ok, detail=""):
    (PASS if ok else FAIL).append(label)
    print(("PASS" if ok else "FAIL"), "-", label, "::", detail)


def sql1(stmt):
    out = (_sql(stmt) or "").strip()
    return out.split()[0] if out else None


def register(tag, seq, nickname):
    username = f"vfyr{tag}_{STAMP}"
    phone = "13" + str(int(STAMP[-5:]) * 10 + seq).zfill(9)[-9:]
    st, body = req("POST", "/auth/register", {
        "username": username, "password": PASSWORD, "phone": phone, "nickname": nickname,
    })
    if not (st == 200 and body.get("code") == 0):
        raise SystemExit(f"注册失败 {username}: {st} {body}")
    st, body = req("POST", "/auth/login", {"username": username, "password": PASSWORD})
    token = (body.get("data") or {}).get("token")
    uid = int(sql1(f"SELECT id FROM sys_user WHERE username='{username}'"))
    return token, uid


def purge_user(uid):
    _sql(f"DELETE FROM return_request WHERE user_id={uid}")
    _sql(f"DELETE FROM stock_log WHERE order_id IN (SELECT id FROM orders WHERE user_id={uid})")
    _sql(f"DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE user_id={uid})")
    _sql(f"DELETE FROM payment_record WHERE order_id IN (SELECT id FROM orders WHERE user_id={uid})")
    _sql(f"UPDATE orders SET user_coupon_id=NULL WHERE user_id={uid}")
    _sql(f"UPDATE stock_log SET order_id=NULL, operator_id=NULL WHERE operator_id={uid}")
    for t in ("product_review", "wallet_transaction", "user_message", "point_ledger",
              "user_favorite", "price_alert", "cart_item", "user_address", "user_coupon"):
        _sql(f"DELETE FROM {t} WHERE user_id={uid}")
    _sql(f"DELETE FROM orders WHERE user_id={uid}")
    _sql(f"DELETE FROM sys_user WHERE id={uid}")


def main():
    adm = temp_admin("限购管理员")
    sale_id = None
    uid = None
    extra_uids = []
    order_ids = []
    try:
        cat_id = int(sql1("SELECT id FROM product_category ORDER BY id LIMIT 1"))
        store_id = int(sql1("SELECT id FROM store WHERE status=1 ORDER BY id LIMIT 1"))

        now = datetime.now()
        st, body = req("POST", "/admin/flash-sales", {
            "productId": None,
            "productName": "VFYRACE-限购并发品",
            "categoryId": cat_id,
            "price": 99.00,
            "unit": "盒",
            "name": "VFYRACE 限购并发场次",
            "flashPrice": 39.90,
            "totalQuota": TOTAL_QUOTA,
            "perUserLimit": PER_USER_LIMIT,
            "startTime": (now - timedelta(minutes=5)).strftime("%Y-%m-%dT%H:%M:%S"),
            "endTime": (now + timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%S"),
            "status": 1,
        }, adm.token)
        sale = body.get("data") or {}
        sale_id, pid = sale.get("id"), sale.get("productId")
        check("创建 perUserLimit=1 的秒杀场次", bool(sale_id) and bool(pid),
              f"http={st} saleId={sale_id} pid={pid} {body.get('message','')}")
        if not sale_id or not pid:
            return finish()

        token, uid = register("", 3, "限购甲")
        check("注册并登录账号 A", bool(token) and uid > 0, f"uid={uid}")

        def buy(tk):
            return req("POST", "/orders/quick-buy", {
                "productId": pid, "quantity": 1,
                "fulfillmentType": "PICKUP", "pickupStoreId": store_id,
            }, tk)

        # ---------- 场景 1：同一账号 25 路并发猛点 ----------
        barrier = threading.Barrier(CONCURRENCY)
        results = [None] * CONCURRENCY

        def worker(i):
            barrier.wait()
            try:
                results[i] = buy(token)
            except Exception as exc:    # noqa: BLE001
                results[i] = ("EXC", str(exc))

        threads = [threading.Thread(target=worker, args=(i,)) for i in range(CONCURRENCY)]
        t0 = time.time()
        for t in threads:
            t.start()
        for t in threads:
            t.join()
        elapsed = int((time.time() - t0) * 1000)

        codes = [r[0] for r in results]
        wins = [r[1]["data"] for r in results
                if isinstance(r[0], int) and r[0] in (200, 201) and isinstance(r[1], dict) and r[1].get("data")]
        order_ids = [o["id"] for o in wins]
        yes, c409 = codes.count(200) + codes.count(201), codes.count(409)
        check(f"同账号 {CONCURRENCY} 并发猛点：成功单数 == {PER_USER_LIMIT}",
              len(wins) == PER_USER_LIMIT,
              f"success={len(wins)} 409={c409} other={CONCURRENCY - yes - c409} 耗时={elapsed}ms")

        sold = int(sql1(f"SELECT sold_quota FROM flash_sale WHERE id={sale_id}"))
        check(f"soldQuota == {PER_USER_LIMIT}（不多占）", sold == PER_USER_LIMIT, f"sold_quota={sold}")
        stock = int(sql1(f"SELECT stock FROM product WHERE id={pid}"))
        check(f"独立秒杀品库存 == {TOTAL_QUOTA - PER_USER_LIMIT}",
              stock == TOTAL_QUOTA - PER_USER_LIMIT, f"stock={stock}")
        bought = int(sql1(
            "SELECT coalesce(sum(i.quantity),0) from order_item i join orders o on o.id=i.order_id "
            f"where o.user_id={uid} and i.flash_sale_id={sale_id} and o.status not in ('CANCELED','CLOSED')"))
        check(f"账号 A 实际占用名额 == {PER_USER_LIMIT}", bought == PER_USER_LIMIT, f"bought={bought}")

        # ---------- 场景 2：3 个不同用户并发各买 1 件 → 都应成功（确认没修过头） ----------
        buyers = []
        for i in range(3):
            tk, u = register(chr(ord('b') + i), 10 + i, f"限购{chr(ord('B') + i)}")
            extra_uids.append(u)
            buyers.append((tk, u))
        b2 = threading.Barrier(len(buyers))
        res2 = [None] * len(buyers)

        def worker2(i):
            b2.wait()
            try:
                res2[i] = buy(buyers[i][0])
            except Exception as exc:    # noqa: BLE001
                res2[i] = ("EXC", str(exc))

        ts2 = [threading.Thread(target=worker2, args=(i,)) for i in range(len(buyers))]
        for t in ts2:
            t.start()
        for t in ts2:
            t.join()
        ok2 = [r for r in res2 if isinstance(r[0], int) and r[0] in (200, 201)]
        for r in ok2:
            order_ids.append(r[1]["data"]["id"])
        msgs2 = [(r[0], (r[1] or {}).get("message") if isinstance(r[1], dict) else r[1]) for r in res2]
        check("3 个不同用户并发各买 1 件：全部成功（限额只针对单人）",
              len(ok2) == len(buyers), f"success={len(ok2)}/{len(buyers)} results={msgs2}")
        sold_all = int(sql1(f"SELECT sold_quota FROM flash_sale WHERE id={sale_id}"))
        check(f"累计占用名额 == {PER_USER_LIMIT + len(buyers)}",
              sold_all == PER_USER_LIMIT + len(buyers), f"sold_quota={sold_all}")
        stock_all = int(sql1(f"SELECT stock FROM product WHERE id={pid}"))
        check(f"库存 == {TOTAL_QUOTA - PER_USER_LIMIT - len(buyers)}",
              stock_all == TOTAL_QUOTA - PER_USER_LIMIT - len(buyers), f"stock={stock_all}")

    finally:
        # 自清：先取消成功订单（释放名额/库存），再删场次、订单、账号
        try:
            # 走后台取消接口（用户 token 在场景切换后不便于逐个保存），会正常释放名额与库存
            for oid in order_ids:
                req("POST", f"/admin/orders/{oid}/cancel", None, adm.token)
        except Exception:               # noqa: BLE001
            pass
        try:
            for u in ([uid] if uid else []) + extra_uids:
                purge_user(u)
            if sale_id:
                _sql(f"DELETE FROM stock_log WHERE product_id IN (SELECT id FROM flash_sale WHERE id={sale_id})")
                _sql(f"DELETE FROM flash_sale WHERE id={sale_id}")
                _sql("DELETE FROM product WHERE name LIKE 'VFYRACE-%'")
        except Exception as exc:        # noqa: BLE001
            print(f"[cleanup] ⚠️ {exc}")


def finish():
    print("\n=== 汇总 ===")
    print(f"通过 {len(PASS)} / 失败 {len(FAIL)}")
    if FAIL:
        print("失败项：", FAIL)
    sys.exit(1 if FAIL else 0)


if __name__ == "__main__":
    main()
    finish()
