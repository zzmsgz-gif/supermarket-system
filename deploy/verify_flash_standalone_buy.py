#!/usr/bin/env python3
"""验证：「脱离原商品」新建的独立秒杀商品能真正按秒杀价下单（无 SKU、无原商品也要走通）。

用法（在 deploy/ 下）：  python verify_flash_standalone_buy.py
"""
import json
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta

from _admin_token import temp_admin

BASE = "http://localhost:8080/api"
PASS, FAIL = [], []


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


def check(label, ok, detail=""):
    (PASS if ok else FAIL).append(label)
    print(("PASS" if ok else "FAIL"), "-", label, "::", detail)


def main():
    with temp_admin() as adm:
        tok = adm.token
        cat_id = int(adm.sql("SELECT id FROM product_category ORDER BY id LIMIT 1").split()[0])
        store_id = int(adm.sql("SELECT id FROM store WHERE status=1 ORDER BY id LIMIT 1").split()[0])
        check("取到自提门店", store_id > 0, f"storeId={store_id}")

        now = datetime.now()
        # 开始时间放在过去 —— 让场次处于 RUNNING，才能真的按秒杀价成交
        start = (now - timedelta(minutes=5)).strftime("%Y-%m-%dT%H:%M:%S")
        end = (now + timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%S")

        payload = {
            "productId": None,
            "productName": "VFYBUY-独立秒杀品",
            "categoryId": cat_id,
            "price": 99.00,
            "unit": "盒",
            "name": "VFYBUY 独立秒杀场次",
            "flashPrice": 39.90,
            "totalQuota": 10,
            "perUserLimit": 2,
            "startTime": start,
            "endTime": end,
            "status": 1,
        }
        st, body = req("POST", "/admin/flash-sales", payload, tok)
        sale = body.get("data") or {}
        sale_id, pid = sale.get("id"), sale.get("productId")
        check("创建独立秒杀（无原商品）", st in (200, 201) and pid, f"status={st} pid={pid}")
        if not pid:
            print(json.dumps(body, ensure_ascii=False)[:300]); return

        # 确认场次确实在进行中
        st, body = req("GET", "/flash-sales", None, tok)
        mine = next((s for s in (body.get("data") or []) if s.get("id") == sale_id), None)
        check("场次处于进行中", (mine or {}).get("state") == "RUNNING",
              f"state={(mine or {}).get('state')}")

        # 立即购买 1 件 —— 应命中秒杀价 39.90
        st, body = req("POST", "/orders/quick-buy", {
            "productId": pid, "quantity": 1,
            "fulfillmentType": "PICKUP", "pickupStoreId": store_id,
        }, tok)
        order = body.get("data") or {}
        check("独立秒杀品可下单", st in (200, 201) and order.get("id"),
              f"status={st} msg={body.get('message','')}")
        if order.get("id"):
            pay = float(order.get("payAmount") or 0)
            check("按秒杀价成交（39.90）", abs(pay - 39.90) < 0.01, f"payAmount={pay}")
            # 取消回补库存与名额
            st2, _ = req("POST", f"/orders/{order['id']}/cancel", None, tok)
            check("取消订单成功", st2 in (200, 201, 204), f"status={st2}")

        # 清理：删场次（连带软删商品）
        st, _ = req("DELETE", f"/admin/flash-sales/{sale_id}", None, tok)
        check("删除场次", st in (200, 201, 204), f"status={st}")
        # 商品已由接口软删（deleted=1），不要硬删：order_item 有外键指向 product，硬删会报 1451

    print("\n=== 汇总 ===")
    print(f"通过 {len(PASS)} / 失败 {len(FAIL)}")
    if FAIL:
        print("失败项：", FAIL)
    sys.exit(1 if FAIL else 0)


if __name__ == "__main__":
    main()
