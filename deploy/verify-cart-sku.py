#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验证购物车「同商品不同规格」修复 + 库存校验不复现误报。
覆盖：
  1) 同商品换规格 -> 两条独立购物车行，规格各自正确、互不被覆盖；
  2) 同规格再加 -> 仅该规格数量累加；
  3) 不同规格不会偷加数量导致误报「库存不足」；
  4) 超过商品真实库存时仍正确拒绝（409）。
自建账号与购物车数据，finally 中按 FK 顺序清理。
"""
import json
import subprocess
import sys
import time
from urllib import request, error as urllib_error

BASE = "http://localhost:8080/api"
STAMP = time.strftime("%Y%m%d%H%M%S")
MYSQL = r"C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"

passed = 0
failed = 0
results = []


def check(name, cond, extra=""):
    global passed, failed
    if cond:
        passed += 1
        results.append(f"  PASS  {name}")
    else:
        failed += 1
        results.append(f"  FAIL  {name}  {extra}")


def call(method, path, body=None, token=None):
    url = BASE + path
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with request.urlopen(req, timeout=10) as r:
            return r.status, json.loads(r.read().decode("utf-8") or "{}")
    except urllib_error.HTTPError as e:
        try:
            payload = json.loads(e.read().decode("utf-8") or "{}")
        except Exception:
            payload = {"message": e.reason}
        return e.code, payload


def data_of(resp):
    return resp[1].get("data") if isinstance(resp[1], dict) else None


def sql(stmt):
    rc = subprocess.run([MYSQL, "-uroot", "-pzzmsgz", "supermarket_system", "-e", stmt],
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode
    return rc == 0


def main():
    buyer = f"s3buyer_{STAMP}"
    phone = "138" + STAMP[-8:]
    tok = data_of(call("POST", "/auth/register",
               {"username": buyer, "password": "S3test123", "nickname": "S3买家",
                "phone": phone, "email": f"{buyer}@example.com"}))
    tok = (tok or {}).get("token")
    check("注册买家账号", bool(tok))
    if not tok:
        print("\n".join(results)); print(f"\n{passed} passed, {failed} failed"); sys.exit(1)

    PID = 35  # 有 2 个 SKU 的商品
    SPEC_A = "color:red size:M"
    SPEC_B = "color:blue size:L"

    # 1) 加规格 A
    st, _ = call("POST", "/cart/items", {"productId": PID, "quantity": 1, "skuSpec": SPEC_A}, tok)
    check("加入规格A成功", st == 200, f"status={st}")
    # 2) 加规格 B（不同规格，应独立成行）
    st, _ = call("POST", "/cart/items", {"productId": PID, "quantity": 1, "skuSpec": SPEC_B}, tok)
    check("加入规格B成功", st == 200, f"status={st}")
    # 取购物车
    st, cart = call("GET", "/cart", token=tok)
    items = data_of((st, cart)).get("items", [])
    check("购物车返回两条独立行", len(items) == 2, f"实际行数={len(items)}")
    specs = {it.get("skuSpec") for it in items}
    check("两行规格分别为 A 和 B", specs == {SPEC_A, SPEC_B}, f"specs={specs}")
    qtys = {it.get("skuSpec"): it.get("quantity") for it in items}
    check("规格A数量=1", qtys.get(SPEC_A) == 1, f"qtys={qtys}")
    check("规格B数量=1（未被覆盖/未被累加）", qtys.get(SPEC_B) == 1, f"qtys={qtys}")

    # 3) 再次加规格 A（同规格应累加）
    st, _ = call("POST", "/cart/items", {"productId": PID, "quantity": 2, "skuSpec": SPEC_A}, tok)
    check("再次加入规格A成功", st == 200, f"status={st}")
    st, cart = call("GET", "/cart", token=tok)
    items = data_of((st, cart)).get("items", [])
    qtys = {it.get("skuSpec"): it.get("quantity") for it in items}
    check("仍是两条独立行", len(items) == 2, f"行数={len(items)}")
    check("规格A累加为3", qtys.get(SPEC_A) == 3, f"qtys={qtys}")
    check("规格B仍=1（异规格未受影响）", qtys.get(SPEC_B) == 1, f"qtys={qtys}")

    # 4) 同规格加超库存应被正确拒绝（商品35库存=10，当前A=3 -> 加8=11>10 -> 409）
    st, _ = call("POST", "/cart/items", {"productId": PID, "quantity": 8, "skuSpec": SPEC_A}, tok)
    check("超过真实库存被正确拒绝(409)", st == 409, f"status={st}")
    # 小数量加购不再误报
    st, _ = call("POST", "/cart/items", {"productId": PID, "quantity": 1, "skuSpec": SPEC_B}, tok)
    check("异规格再加1件不误报库存不足", st == 200, f"status={st}")


def cleanup():
    try:
        out = subprocess.run(
            [MYSQL, "-uroot", "-pzzmsgz", "-N", "-B", "-e",
             "SELECT id FROM sys_user WHERE username LIKE 's3buyer_%'",
             "supermarket_system"],
            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True
        ).stdout.strip().replace("\n", ",")
        if out:
            sql(f"DELETE FROM user_address WHERE user_id IN ({out});"
                f"DELETE FROM cart_item WHERE user_id IN ({out});"
                f"DELETE FROM user_coupon WHERE user_id IN ({out});"
                f"DELETE FROM wallet_transaction WHERE user_id IN ({out});"
                f"DELETE FROM product_review WHERE user_id IN ({out});"
                f"DELETE FROM page_dwell WHERE user_id IN ({out});"
                f"DELETE FROM sys_user WHERE id IN ({out});")
    except Exception as e:
        print("cleanup warn:", e)


if __name__ == "__main__":
    try:
        main()
    finally:
        cleanup()
    print("\n".join(results))
    print(f"\n{passed} passed, {failed} failed")
    sys.exit(1 if failed else 0)
