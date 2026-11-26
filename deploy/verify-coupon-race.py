#!/usr/bin/env python3
"""领券「每人限领一次」并发回归：同一账号并发猛点，只能领到 1 张。

背景（2026-11-02）：与秒杀限购同源 —— 「先查是否领过 → 再插入」在 REPEATABLE READ 下
第二笔读的是快照，看不到第一笔刚插入的 user_coupon，于是「限领一次」的券能领到两张。
修法：receiveCoupon 事务显式 READ_COMMITTED + 券行悲观锁串行化（不再用 FOR UPDATE 的 count，
那会在 RR 下引间隙锁 → 死锁）。

断言：同账号 10 并发领取 claimType=0 的券 → 成功 1 次，user_coupon 只有 1 条，received_count=1。

用法（deploy 下）： python verify-coupon-race.py
自建自清：临时券 + 临时账号全部清理。
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
CONCURRENCY = 10
PASS, FAIL = [], []


def req(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method,
                               headers={"Content-Type": "application/json"})
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=25) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:           # noqa: BLE001
            return e.code, {}
    except Exception as exc:        # noqa: BLE001
        return "EXC", str(exc)


def check(label, ok, detail=""):
    (PASS if ok else FAIL).append(label)
    print(("PASS" if ok else "FAIL"), "-", label, "::", detail)


def sql1(stmt):
    out = (_sql(stmt) or "").strip()
    return out.split()[0] if out else None


def main():
    adm = temp_admin("领券管理员")
    coupon_id, uid = None, None
    try:
        now = datetime.now()
        st, body = req("POST", "/admin/coupons", {
            "name": f"VFYC并发券{STAMP}",
            "thresholdAmount": 10,
            "discountAmount": 5,
            "totalCount": 100,
            "claimType": 0,          # 0 = 每人限领一次
            "startTime": (now - timedelta(minutes=5)).strftime("%Y-%m-%dT%H:%M:%S"),
            "endTime": (now + timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%S"),
        }, adm.token)
        coupon_id = (body.get("data") or {}).get("id")
        check("创建「每人限领一次」的券", bool(coupon_id), f"http={st} couponId={coupon_id} {body.get('message','')}")
        if not coupon_id:
            return finish()

        username = f"vfyc{STAMP}"
        phone = "13" + str(int(STAMP[-5:]) * 10 + 5).zfill(9)[-9:]
        req("POST", "/auth/register", {"username": username, "password": PASSWORD,
                                       "phone": phone, "nickname": "领券甲"})
        st, body = req("POST", "/auth/login", {"username": username, "password": PASSWORD})
        token = (body.get("data") or {}).get("token")
        uid = int(sql1(f"SELECT id FROM sys_user WHERE username='{username}'"))
        check("注册并登录账号", bool(token) and uid > 0, f"uid={uid}")

        barrier = threading.Barrier(CONCURRENCY)
        res = [None] * CONCURRENCY

        def worker(i):
            barrier.wait()
            try:
                res[i] = req("POST", f"/coupons/{coupon_id}/receive", None, token)
            except Exception as exc:   # noqa: BLE001
                res[i] = ("EXC", str(exc))

        ts = [threading.Thread(target=worker, args=(i,)) for i in range(CONCURRENCY)]
        t0 = time.time()
        for t in ts:
            t.start()
        for t in ts:
            t.join()
        elapsed = int((time.time() - t0) * 1000)

        codes = [r[0] for r in res]
        ok_n = codes.count(200) + codes.count(201)
        check(f"{CONCURRENCY} 并发领取：成功 1 次", ok_n == 1,
              f"success={ok_n} 409={codes.count(409)} other={[c for c in codes if c not in (200, 201, 409)]} 耗时={elapsed}ms")

        rows = int(sql1(f"SELECT count(*) FROM user_coupon WHERE user_id={uid} AND coupon_id={coupon_id}"))
        check("user_coupon 只有 1 条", rows == 1, f"rows={rows}")
        received = int(sql1(f"SELECT received_count FROM coupon WHERE id={coupon_id}"))
        check("received_count == 1", received == 1, f"received_count={received}")

    finally:
        try:
            if uid:
                _sql(f"DELETE FROM user_coupon WHERE user_id={uid}")
            if coupon_id:
                _sql(f"DELETE FROM user_coupon WHERE coupon_id={coupon_id}")
                _sql(f"DELETE FROM coupon WHERE id={coupon_id}")
            if uid:
                _sql(f"DELETE FROM sys_user WHERE id={uid}")
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
