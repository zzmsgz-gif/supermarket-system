#!/usr/bin/env python3
"""生产复验：秒杀「每人限购」并发绕过是否已修好（对标用户实测的 sale 33 / 帝王蟹）。

做法：临时账号对 perUserLimit=1 的场次并发猛点，断言**只有 1 单成功**，
随后再串行补点几次必须全部 409，最后取消订单释放名额。

⚠️ 会真实占用 1 个名额与 1 件库存，脚本结束前取消释放；账号由调用方用 SQL 清（末尾打印用户名）。

用法：
    SM_BASE=https://haomart.xyz/api SM_PID=185 SM_STORE=1 SM_CONC=25 python verify-flash-limit-prod.py
"""
import json
import os
import sys
import threading
import time
import urllib.error
import urllib.request

BASE = os.environ.get("SM_BASE", "https://haomart.xyz/api").rstrip("/")
PRODUCT_ID = int(os.environ.get("SM_PID", "185"))
STORE_ID = int(os.environ.get("SM_STORE", "1"))
CONCURRENCY = int(os.environ.get("SM_CONC", "25"))
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
        with urllib.request.urlopen(r, timeout=30) as resp:
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


def buy(token):
    return req("POST", "/orders/quick-buy", {
        "productId": PRODUCT_ID, "quantity": 1,
        "fulfillmentType": "PICKUP", "pickupStoreId": STORE_ID,
    }, token)


def main():
    username = f"vfyfp_{STAMP}"
    phone = "13" + str(int(STAMP[-5:]) * 10 + 9).zfill(9)[-9:]
    st, body = req("POST", "/auth/register", {"username": username, "password": PASSWORD,
                                              "phone": phone, "nickname": "限购复验"})
    check("注册临时账号", st == 200 and body.get("code") == 0, f"http={st} {username}")
    st, body = req("POST", "/auth/login", {"username": username, "password": PASSWORD})
    token = (body.get("data") or {}).get("token")
    check("登录", bool(token), f"http={st}")
    if not token:
        print("USERNAME=" + username)
        return finish()

    # ---------- 25 路并发猛点 ----------
    barrier = threading.Barrier(CONCURRENCY)
    res = [None] * CONCURRENCY

    def worker(i):
        barrier.wait()
        try:
            res[i] = buy(token)
        except Exception as exc:    # noqa: BLE001
            res[i] = ("EXC", str(exc))

    ts = [threading.Thread(target=worker, args=(i,)) for i in range(CONCURRENCY)]
    t0 = time.time()
    for t in ts:
        t.start()
    for t in ts:
        t.join()
    elapsed = int((time.time() - t0) * 1000)

    codes = [r[0] for r in res]
    wins = [r[1]["data"] for r in res
            if isinstance(r[0], int) and r[0] in (200, 201) and isinstance(r[1], dict) and r[1].get("data")]
    c429 = codes.count(429)
    print(f"[并发] success={len(wins)} 409={codes.count(409)} 429={c429} "
          f"other={[c for c in codes if c not in (200, 201, 409, 429)]} 耗时={elapsed}ms")

    # 被网关限流吃掉一部分时，串行补点到「成功 1 次」为止，保证确实打到了下单链路
    tries = 0
    while not wins and tries < 5:
        tries += 1
        st, body = buy(token)
        if st in (200, 201) and (body.get("data") or {}).get("id"):
            wins.append(body["data"])
            print(f"[补点 {tries}] 成功一次（说明前面是被 429 拦的，不是限购生效）")
        else:
            print(f"[补点 {tries}] http={st} msg={body.get('message','')}")
        time.sleep(0.3)

    check("并发猛点 + 补点：只成功 1 单", len(wins) == 1,
          f"success={len(wins)} 409={codes.count(409)} 429={c429}")

    # ---------- 串行再点 3 次，必须全部 409（限购真的生效） ----------
    refuses = []
    for _ in range(3):
        st, body = buy(token)
        refuses.append((st, (body or {}).get("message")))
        time.sleep(0.2)
    check("已买满后再点 3 次全部被拒", all(c == 409 for c, _ in refuses), f"{refuses}")

    # ---------- 取消，释放名额 ----------
    if wins:
        oid = wins[0]["id"]
        st, _ = req("POST", f"/orders/{oid}/cancel", None, token)
        check("取消订单释放名额", st in (200, 201, 204), f"orderId={oid} http={st}")
        print("ORDER_ID=" + str(oid))
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
