#!/usr/bin/env python3
"""验证：管理员订单详情接口是否返回商品项（Bug1）。

管理员登录态用**临时账号**（`_admin_token.temp_admin`）—— 这里原本会把 admin(id=1) 的密码
临时改成硬编码值再改回，一旦中途崩溃（429 中断 / 超时强杀 / assert 失败）就会把 admin 密码
**留在那个硬编码值上**，而本仓库是公开仓库。现在 id=1 全程不碰（见 PROGRESS 未决项①）。

前置：后端在 8080 跑着；订单 11 存在且含商品项。
"""
import json
import sys
import urllib.error
import urllib.request

from _admin_token import temp_admin

BASE = "http://localhost:8080/api"


def req(method, path, token=None, body=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=15) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {"raw": e.read().decode()}


with temp_admin() as adm:
    print(f"临时管理员 {adm.username}（id={adm.uid}）；admin(id=1) 的密码未被改动")

    # 动态取一个「含商品项」的订单 —— 原来写死 order 11，那条早已被清理掉，脚本一直跑不通
    ORDER_ID = int(adm.sql(
        "SELECT o.id FROM orders o JOIN order_item oi ON oi.order_id=o.id "
        "GROUP BY o.id ORDER BY o.id LIMIT 1") or 0)
    if not ORDER_ID:
        raise SystemExit("库里没有含商品项的订单，无法验证")

    # 用该订单验证管理员订单详情
    code, resp = req("GET", f"/admin/orders/{ORDER_ID}", token=adm.token)
    od = resp.get("data") or {}
    items = od.get("items") or []
    ok = len(items) >= 1 and items[0].get("productName") and items[0].get("quantity")
    print(("PASS " if ok else "FAIL ")
          + f"Bug1-管理员订单详情含商品 order={ORDER_ID} items={len(items)} "
            f"name={items[0].get('productName') if items else None}"
            + ("" if items else f"  (HTTP {code}: {str(resp)[:120]})"))

    # 售后管理同样复用该接口，顺带确认退款列表也能按订单取到详情所需的字段
    ok_fields = all(k in od for k in ("orderNo", "status", "items")) if od else False
    print(("PASS " if ok_fields else "FAIL ") + f"Bug1-详情字段齐全(orderNo/status/items) keys={sorted(od)[:8]}")

ok = ok and ok_fields
sys.exit(0 if ok else 1)
