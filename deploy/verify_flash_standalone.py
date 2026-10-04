#!/usr/bin/env python3
"""验证：秒杀可以「脱离原商品」新建独立商品 —— 后台建场、校验、列表可见性、下单链路、清理。

用法（在 deploy/ 下）：  python verify_flash_standalone.py
"""
import json
import sys
from datetime import datetime, timedelta

import urllib.error
import urllib.request

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

        # ---------- 1. 取一个分类（商品表 category_id 非空） ----------
        cat_id = int(adm.sql("SELECT id FROM product_category ORDER BY id LIMIT 1").split()[0])
        check("取到分类", cat_id > 0, f"categoryId={cat_id}")

        # ---------- 0. 清掉上一次（失败）运行残留的 VFY 数据，让脚本可重复跑 ----------
        leftovers = adm.sql("SELECT id, product_id FROM flash_sale WHERE name LIKE 'VFY%'")
        for line in (leftovers.splitlines() if leftovers else []):
            parts = line.split("\t") if "\t" in line else line.split()
            if len(parts) < 2:
                continue
            sid, spid = parts[0].strip(), parts[1].strip()
            adm.sql(f"DELETE FROM flash_sale WHERE id={sid}")
            adm.sql(f"DELETE FROM product_sku WHERE product_id={spid}")
            adm.sql(f"DELETE FROM product WHERE id={spid}")
        if leftovers:
            print(f"[cleanup] 已清理残留：{leftovers}")

        now = datetime.now()
        start = (now + timedelta(minutes=2)).strftime("%Y-%m-%dT%H:%M:%S")
        end = (now + timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%S")

        # ---------- 2. 建一个「无原商品」的独立秒杀 ----------
        payload = {
            "productId": None,                    # 关键：不给原商品
            "productName": "VFY-独立测试商品",
            "categoryId": cat_id,
            "price": 99.00,
            "originalPrice": 129.00,
            "unit": "盒",
            "name": "VFY 独立秒杀场次",
            "flashPrice": 39.90,
            "totalQuota": 10,
            "perUserLimit": 2,
            "startTime": start,
            "endTime": end,
            "status": 1,
            "sortNo": 0,
        }
        st, body = req("POST", "/admin/flash-sales", payload, tok)
        ok = st in (200, 201) and body.get("data")
        check("创建独立秒杀场次（无原商品）", ok, f"status={st} msg={body.get('message','')}")
        if not ok:
            print(json.dumps(body, ensure_ascii=False)[:400])
            return
        sale = body["data"]
        sale_id = sale["id"]
        pid = sale["productId"]

        check("响应 sourceProductId 为空", sale.get("sourceProductId") is None,
              f"sourceProductId={sale.get('sourceProductId')}")
        check("响应带上商品名与售价", sale.get("productName") == "VFY-独立测试商品"
              and float(sale.get("price") or 0) == 99.0,
              f"productName={sale.get('productName')} price={sale.get('price')}")

        # ---------- 3. 商品真的建出来了，且是 FLASH 独立商品 ----------
        row = adm.sql(f"SELECT kind, stock, price, original_price, unit, deleted FROM product WHERE id={pid}")
        cols = row.split("\t") if "\t" in row else row.split()
        check("商品 kind=FLASH", len(cols) > 0 and cols[0] == "FLASH", f"row={row}")
        check("商品库存=名额", len(cols) > 1 and cols[1] == "10", f"stock={cols[1] if len(cols)>1 else '?'}")
        check("商品售价/划线价/单位正确",
              len(cols) > 4 and cols[2].startswith("99") and cols[4] == "盒", f"row={row}")

        # ---------- 4. 公开秒杀列表能看到它 ----------
        st, body = req("GET", "/flash-sales")
        sales = body.get("data") or []
        check("公开秒杀列表可见", any(s.get("id") == sale_id for s in sales),
              f"命中={any(s.get('id') == sale_id for s in sales)}")

        # ---------- 5. 公开商品列表仍然看不到它（FLASH 被排除） ----------
        st, body = req("GET", "/products?keyword=VFY-%E7%8B%AC%E7%AB%8B%E6%B5%8B%E8%AF%95")
        items = (body.get("data") or {}).get("items") if isinstance(body.get("data"), dict) else (body.get("data") or [])
        hit = any(i.get("id") == pid for i in items)
        check("公开商品列表排除该秒杀品", not hit, f"被搜到={hit}")

        # ---------- 6. 校验独立商品必填项 ----------
        bad = dict(payload)
        bad.pop("price")
        st, body = req("POST", "/admin/flash-sales", bad, tok)
        check("缺售价被拒", st == 400 and "售价" in (body.get("message") or ""),
              f"status={st} msg={body.get('message','')}")

        bad = dict(payload)
        bad["flashPrice"] = 199
        st, body = req("POST", "/admin/flash-sales", bad, tok)
        check("秒杀价高于售价被拒", st == 400 and "秒杀价必须低于" in (body.get("message") or ""),
              f"status={st} msg={body.get('message','')}")

        bad = dict(payload)
        bad.pop("categoryId")
        st, body = req("POST", "/admin/flash-sales", bad, tok)
        check("缺分类被拒", st == 400 and "分类" in (body.get("message") or ""),
              f"status={st} msg={body.get('message','')}")

        # ---------- 7. 编辑独立秒杀（改商品名/售价） ----------
        upd = dict(payload)
        upd["productName"] = "VFY-独立测试商品(改)"
        upd["price"] = 88.00
        upd["flashPrice"] = 29.90
        st, body = req("PUT", f"/admin/flash-sales/{sale_id}", upd, tok)
        ok = st in (200, 201) and (body.get("data") or {}).get("productName") == "VFY-独立测试商品(改)"
        check("编辑独立秒杀（改商品名/售价）", ok, f"status={st} msg={body.get('message','')}")
        row2 = adm.sql(f"SELECT name, price FROM product WHERE id={pid}")
        check("商品表同步到新名/新价", "改" in row2 and row2.split("\t")[-1].startswith("88"),
              f"row={row2}")

        # ---------- 8. 删除场次 → 秒杀商品一并软删 ----------
        st, body = req("DELETE", f"/admin/flash-sales/{sale_id}", None, tok)
        check("删除场次成功", st in (200, 201, 204), f"status={st} msg={body.get('message','')}")
        row3 = adm.sql(f"SELECT deleted FROM product WHERE id={pid}")
        check("秒杀商品随场次软删", row3.strip() == "1", f"deleted={row3.strip()}")

    print("\n=== 汇总 ===")
    print(f"通过 {len(PASS)} / 失败 {len(FAIL)}")
    if FAIL:
        print("失败项：", FAIL)
    sys.exit(1 if FAIL else 0)


if __name__ == "__main__":
    main()
