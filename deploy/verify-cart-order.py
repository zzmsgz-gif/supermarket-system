#!/usr/bin/env python3
"""购物车顺序稳定性回归：改数量不能让商品在列表里跳位置。

背景（2026-11-27 用户反馈）：
    「PC端和程序端添加进购物车的商品不要再因为数量变化而改变它的顺序了，
      每次我添加一个商品的数量，添加几次它就跑上面去了，很影响手感」

根因：`CartService.getCart` 用的是 `findByUserIdOrderByUpdatedAtDesc`。
改数量会刷新 `updated_at`，那一行就被顶到列表最前面。
修法：改按 `created_at DESC, id DESC`（加入时间）排序。

用法：
    python deploy/_verify_cart_order.py                 # 打本地 http://localhost:8080/api
    VERIFY_BASE=https://haomart.xyz/api python ...      # 打线上

期望：全部 PASS，退出码 0。
脚本自建自清：注册临时用户 → 跑断言 → 清空购物车 → 删账号（不留垃圾）。
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

from _admin_token import _sql

BASE = os.environ.get("VERIFY_BASE", "http://localhost:8080/api")
PASSWORD = "TmpUser12345"
USER_PREFIX = "vtmpcart"

PASS, FAIL = [], []


def _req(method, path, body=None, token=None):
    """自带一份请求器而不用 _admin_token 的：那个把 BASE 写死在 localhost:8080，
    这里要支持 VERIFY_BASE 打线上。"""
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method,
                               headers={"Content-Type": "application/json"})
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=20) as resp:
            return resp.status, json.loads(resp.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode() or "{}")
        except Exception:
            return e.code, {}


def check(label, cond, detail=""):
    if cond:
        PASS.append(label)
        print(f"PASS  {label}")
    else:
        FAIL.append(label)
        print(f"FAIL  {label}  {detail}")


def sql(statement):
    return _sql(statement)


def cleanup(username):
    """按 CLEANUP.md 的顺序清：用户级流水 → 账号。购物车行也在流水列表里。"""
    uid = sql(f"SELECT id FROM sys_user WHERE username='{username}'")
    uid = uid.split()[0] if uid else None
    if not uid:
        return
    for table in ("product_review", "wallet_transaction", "user_message", "point_ledger",
                  "user_favorite", "price_alert", "cart_item", "user_address", "user_coupon"):
        try:
            sql(f"DELETE FROM {table} WHERE user_id={uid}")
        except Exception:
            pass
    try:
        sql(f"UPDATE stock_log SET operator_id=NULL WHERE operator_id={uid}")
    except Exception:
        pass
    sql(f"DELETE FROM sys_user WHERE id={uid}")


def main():
    ts = int(time.time())
    username = f"{USER_PREFIX}_{ts}"
    phone = "13" + str(ts)[-9:].zfill(9)

    st, body = _req("POST", "/auth/register",
                    {"username": username, "password": PASSWORD, "phone": phone,
                     "nickname": "顺序验证"})
    if body.get("code") != 0:
        print("注册临时账号失败：", st, body)
        return 2

    st, body = _req("POST", "/auth/login", {"username": username, "password": PASSWORD})
    token = (body.get("data") or {}).get("token")
    if not token:
        print("登录失败：", body)
        cleanup(username)
        return 2

    try:
        # 取 3 个有库存的在售商品（排除秒杀克隆品，避免名额校验干扰）
        st, body = _req("GET", "/products?size=60", token=token)
        pool = [p for p in (body.get("data") or {}).get("items", [])
                if int(p.get("stock") or 0) > 2 and (p.get("kind") or "") != "FLASH"]
        # 按 id 排序，保证每次跑选的是同一批商品，结果可复现
        pool.sort(key=lambda p: p["id"])
        if len(pool) < 3:
            print("可用商品不足 3 个，跳过")
            return 2
        a, b, c = pool[0], pool[1], pool[2]

        for p in (a, b, c):
            st, body = _req("POST", "/cart/items",
                            {"productId": p["id"], "quantity": 1}, token=token)
            if body.get("code") != 0:
                print(f"加购商品 {p['id']} 失败：", body)
                return 2
            time.sleep(0.3)   # 拉开 created_at，模拟真实点击节奏

        def order():
            st, body = _req("GET", "/cart", token=token)
            items = (body.get("data") or {}).get("items", [])
            return [it["productId"] for it in items], items

        seq, items = order()
        expect = [c["id"], b["id"], a["id"]]   # 最新加入的在最上面
        check("初始顺序 = 加入时间倒序", seq == expect, f"实际 {seq} 期望 {expect}")

        # 核心场景：把**最后一件**（最早加入的）数量改大，它不能跳到最前
        st, body = _req("PUT", f"/cart/items/{items[-1]['id']}", {"quantity": 3}, token=token)
        seq2, items2 = order()
        check("改最后一件的数量后顺序不变", seq2 == expect, f"实际 {seq2} 期望 {expect}")
        hit = next((it for it in items2 if it["productId"] == a["id"]), None)
        check("数量已生效 = 3", hit and int(hit["quantity"]) == 3, f"实际 {hit and hit.get('quantity')}")

        # 中间那件也改一次
        mid = next(it for it in items2 if it["productId"] == b["id"])
        st, body = _req("PUT", f"/cart/items/{mid['id']}", {"quantity": 5}, token=token)
        seq3, items3 = order()
        check("改中间一件的数量后顺序不变", seq3 == expect, f"实际 {seq3} 期望 {expect}")

        # 最新加入的那件改数量，也应留在原位
        top = next(it for it in items3 if it["productId"] == c["id"])
        st, body = _req("PUT", f"/cart/items/{top['id']}", {"quantity": 2}, token=token)
        seq4, items4 = order()
        check("改第一件的数量后顺序不变", seq4 == expect, f"实际 {seq4} 期望 {expect}")

        # 数量总和要跟着变（顺带确认购物车金额口径没被这次改动带坏）
        total_qty = sum(int(it["quantity"]) for it in items4)
        check("购物车总件数 = 10", total_qty == 10, f"实际 {total_qty}")

        # 清空
        st, body = _req("DELETE", "/cart", token=token)
        check("清空购物车成功", body.get("code") == 0, str(body))
    finally:
        cleanup(username)

    print(f"\n{len(PASS)} PASS / {len(FAIL)} FAIL")
    return 0 if not FAIL else 1


if __name__ == "__main__":
    sys.exit(main())
