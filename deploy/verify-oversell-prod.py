#!/usr/bin/env python3
"""生产轻量并发验证：注册临时号 → 下单 1 件 → 并发取消两次 → 库存必须只回补 1 次。

只验证一件事，但正是「超卖」的根：**同一张订单的库存只能被还一次**。
旧代码在并发取消下会还两次（库存 +2），定时关单与用户取消撞上同理。

⚠️ 会在生产真实扣/还 1 件库存（秒级回补）；账号由调用方用 SQL 清（脚本末尾打印用户名）。
用法：
    SM_BASE=https://haomart.xyz/api SM_PID=2 SM_STORE=1 python verify-oversell-prod.py
"""
import json
import os
import sys
import threading
import time
import urllib.error
import urllib.request

BASE = os.environ.get("SM_BASE", "https://haomart.xyz/api").rstrip("/")
PRODUCT_ID = int(os.environ.get("SM_PID", "2"))
STORE_ID = int(os.environ.get("SM_STORE", "1"))
PASSWORD = "TmpUser12345"
STAMP = str(int(time.time()))
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
        except Exception:            # noqa: BLE001
            return e.code, {}
    except Exception as exc:         # noqa: BLE001
        return "EXC", str(exc)


def check(label, ok, detail=""):
    (PASS if ok else FAIL).append(label)
    print(("PASS" if ok else "FAIL"), "-", label, "::", detail)


def stock_now():
    st, body = req("GET", f"/products/{PRODUCT_ID}")
    return (body.get("data") or {}).get("stock") if st == 200 else None


def concurrent(calls):
    barrier = threading.Barrier(len(calls))
    out = [None] * len(calls)

    def wrap(i, fn):
        barrier.wait()
        try:
            out[i] = fn()
        except Exception as exc:      # noqa: BLE001
            out[i] = ("EXC", str(exc))

    ts = [threading.Thread(target=wrap, args=(i, fn)) for i, fn in enumerate(calls)]
    for t in ts:
        t.start()
    for t in ts:
        t.join()
    return out


def main():
    before = stock_now()
    check("读到商品当前库存", before is not None, f"product={PRODUCT_ID} stock={before}")

    username = f"vfypd_{STAMP}"
    phone = "13" + str(int(STAMP[-5:]) * 10 + 7).zfill(9)[-9:]
    st, body = req("POST", "/auth/register", {
        "username": username, "password": PASSWORD, "phone": phone, "nickname": "并发验证",
    })
    check("注册临时买家", st == 200 and body.get("code") == 0, f"http={st} {username}")
    st, body = req("POST", "/auth/login", {"username": username, "password": PASSWORD})
    token = (body.get("data") or {}).get("token")
    check("登录成功", bool(token), f"http={st}")
    if not token:
        print("USERNAME=" + username)
        return finish()

    st, body = req("POST", "/orders/quick-buy", {
        "productId": PRODUCT_ID, "quantity": 1,
        "fulfillmentType": "PICKUP", "pickupStoreId": STORE_ID,
    }, token)
    order = body.get("data") or {}
    check("下单 1 件", st in (200, 201) and order.get("id"), f"http={st} {body.get('message','')}")
    if not order.get("id"):
        print("USERNAME=" + username)
        return finish()
    oid = order["id"]
    check("下单后库存 -1", stock_now() == before - 1, f"stock={stock_now()} expect={before - 1}")

    cancels = concurrent([
        lambda: req("POST", f"/orders/{oid}/cancel", None, token),
        lambda: req("POST", f"/orders/{oid}/cancel", None, token),
    ])
    codes = [c[0] for c in cancels]
    check("并发取消：只有 1 笔成功", codes.count(200) + codes.count(201) == 1, f"statuses={codes}")
    after = stock_now()
    check("库存只回补 1 次（回到原值）", after == before, f"stock={after} expect={before}")
    print("USERNAME=" + username)


def finish():
    print("\n=== 汇总 ===")
    print(f"通过 {len(PASS)} / 失败 {len(FAIL)}")
    if FAIL:
        print("失败项：", FAIL)
    sys.exit(1 if FAIL else 0)


if __name__ == "__main__":
    main()
    finish()
