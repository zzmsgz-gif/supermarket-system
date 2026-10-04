"""秒杀「独立商品」验证：秒杀商品与原商品是两件不同的商品。

背景（需求）：管理员开一场秒杀时，后台克隆出一件**独立的秒杀商品**（kind=FLASH），
秒杀场次指向这件克隆品；原商品完全不动（库存、售价、列表都不受影响）。

覆盖：
  A. 创建秒杀 → 克隆出独立商品（新 id、kind=FLASH、库存=名额、规格价已复制）
  B. 公开商品列表排除 FLASH（原商品照常出现，秒杀商品只在秒杀区）
  C. 秒杀区接口 productId 指向克隆品，sourceProductId 指向原商品
  D. 下单：扣的是【克隆品库存 + 秒杀名额】，原商品库存纹丝不动
  E. 取消订单：克隆品库存与名额一并回退，原商品仍不动
  F. 删除场次：克隆商品一并软删
  G. 原商品不再带秒杀标记（公开秒杀列表里没有 productId=原商品 的场次）

自建账号/场次，finally 清库（沿用 verify-flash-split.py 的清理风格）。
"""
import json
import subprocess
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta

from _db import DB, DBUSER, DBPASS, MYSQL_BIN

BASE = "http://localhost:8080/api"
STAMP = str(int(time.time()))

buyer = "fsep_" + STAMP
admin = "fsepadm_" + STAMP
PW = "Fsep12345"

uid = admin_id = None
created_orders = []
created_sale = None        # 场次 id
created_flash_pid = None   # 克隆出来的秒杀商品 id
source_pid = None          # 原商品 id
results = []


def request(method, path, body=None, token=None):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Content-Type": "application/json",
                 **({"Authorization": "Bearer " + token} if token else {})},
        method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode() or "null")
    except urllib.error.HTTPError as err:
        raw = err.read().decode()
        try:
            return err.code, json.loads(raw or "null")
        except ValueError:
            return err.code, {"raw": raw}


def call(method, path, body=None, token=None):
    status, payload = request(method, path, body, token)
    if status >= 400 or (isinstance(payload, dict) and payload.get("code") not in (0, None)):
        raise RuntimeError(f"{method} {path} -> {status} {payload}")
    return payload.get("data")


def sql(stmt):
    p = subprocess.run([MYSQL_BIN, "-u", DBUSER, "-p" + DBPASS, "--default-character-set=utf8mb4",
                        "-D", DB, "-N", "-B", "-e", stmt],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if p.returncode != 0:
        raise RuntimeError("SQL fail: " + p.stderr)
    return p.stdout.strip()


def check(name, cond, detail=""):
    results.append((name, bool(cond)))
    print(("PASS  " if cond else "FAIL  ") + name + (("  | " + str(detail)) if detail else ""))


def ts(hours_from_now):
    return (datetime.now() + timedelta(hours=hours_from_now)).strftime("%Y-%m-%dT%H:%M")


def int_or(raw, default=0):
    try:
        return int(str(raw).strip())
    except (TypeError, ValueError):
        return default


try:
    # ============ 准备 ============
    admin_tok = call("POST", "/auth/register",
                     {"username": admin, "password": PW, "nickname": "秒杀独立管理员",
                      "phone": "137" + STAMP[-8:]})["token"]
    admin_id = call("GET", "/auth/me", None, admin_tok)["id"]
    sql(f"UPDATE {DB}.sys_user SET role='ADMIN' WHERE id={admin_id}")
    admin_tok = call("POST", "/auth/login", {"username": admin, "password": PW})["token"]

    tok = call("POST", "/auth/register",
               {"username": buyer, "password": PW, "nickname": "秒杀独立买家",
                "phone": "138" + STAMP[-8:]})["token"]
    uid = call("GET", "/auth/me", None, tok)["id"]
    call("POST", "/wallet/recharges", {"amount": 1000}, tok)
    call("POST", "/addresses", {"receiverName": "秒杀收", "receiverPhone": "138" + STAMP[-8:],
                                "province": "广东省", "city": "深圳市", "district": "南山区",
                                "detailAddress": "秒杀路 1 号", "isDefault": True}, tok)
    address_id = call("GET", "/addresses", None, tok)[0]["id"]

    # 挑一个「此刻没有未结束秒杀场次」的普通商品当原商品
    row = sql(
        f"SELECT p.id, p.price, p.name FROM {DB}.product p "
        f"WHERE p.kind='NORMAL' AND p.status='ON_SALE' AND p.deleted=0 AND p.stock>=50 "
        f"AND p.id NOT IN (SELECT source_product_id FROM {DB}.flash_sale "
        f"  WHERE deleted=0 AND status=1 AND end_time>NOW() AND source_product_id IS NOT NULL) "
        f"LIMIT 1")
    if not row:
        raise RuntimeError("找不到可作为原商品的普通商品")
    source_pid, src_price, src_name = row.split("\t")
    source_pid = int(source_pid)
    src_price = float(src_price)
    src_stock_before = int(sql(f"SELECT stock FROM {DB}.product WHERE id={source_pid}"))
    src_sku_count = int_or(sql(
        f"SELECT COUNT(*) FROM {DB}.product_sku WHERE product_id={source_pid} AND deleted=0"), 0)
    print(f"原商品: id={source_pid} {src_name} 价={src_price} 库存={src_stock_before} 规格数={src_sku_count}")

    # ============ A. 创建秒杀 → 克隆独立商品 ============
    sale = call("POST", "/admin/flash-sales", {
        "productId": source_pid, "name": "独立秒杀_" + STAMP,
        "flashPrice": round(src_price * 0.5, 2), "totalQuota": 10, "perUserLimit": 0,
        "startTime": ts(-1), "endTime": ts(24), "status": 1, "sortNo": 9}, admin_tok)
    created_sale = sale["id"]
    created_flash_pid = sale["productId"]

    check("A1 ⭐ 秒杀场次指向的是【新克隆的商品】，不是原商品",
          created_flash_pid != source_pid, f"flashPid={created_flash_pid} sourcePid={source_pid}")
    check("A2 响应透出 sourceProductId（=原商品），便于后台回填下拉",
          sale.get("sourceProductId") == source_pid,
          f"sourceProductId={sale.get('sourceProductId')}")

    fkind = sql(f"SELECT kind FROM {DB}.product WHERE id={created_flash_pid}")
    fname = sql(f"SELECT name FROM {DB}.product WHERE id={created_flash_pid}")
    fstock = int(sql(f"SELECT stock FROM {DB}.product WHERE id={created_flash_pid}"))
    check("A3 ⭐ 克隆商品 kind=FLASH", fkind == "FLASH", f"kind={fkind}")
    check("A4 克隆商品库存 = 秒杀名额（10）", fstock == 10, f"stock={fstock}")
    check("A5 克隆商品名字带出原商品名 + 秒杀标识", src_name in fname, f"name={fname}")
    fsku = int_or(sql(
        f"SELECT COUNT(*) FROM {DB}.product_sku WHERE product_id={created_flash_pid} AND deleted=0"), 0)
    check("A6 规格价已复制到克隆商品（多规格商品才能按折扣率算秒杀价）",
          fsku == src_sku_count, f"克隆规格={fsku} 原商品规格={src_sku_count}")
    src_kind_after = sql(f"SELECT kind FROM {DB}.product WHERE id={source_pid}")
    check("A7 原商品仍是 NORMAL（没被改成秒杀商品）", src_kind_after == "NORMAL", f"kind={src_kind_after}")

    # ============ B. 公开列表排除 FLASH ============
    import urllib.parse
    kw_src = urllib.parse.quote(src_name)
    pub = call("GET", f"/products?keyword={kw_src}&size=50")["items"]
    ids = [p["id"] for p in pub]
    check("B1 ⭐ 公开搜索里没有克隆的秒杀商品（它只在秒杀区出现）",
          created_flash_pid not in ids, f"返回 ids={ids}")
    check("B2 原商品照常出现在公开搜索", source_pid in ids, f"返回 ids={ids}")

    # ============ C. 秒杀区接口 ============
    public_sales = call("GET", "/flash-sales", None, tok)
    mine = next((s for s in public_sales if s["id"] == created_sale), None)
    check("C1 ⭐ 秒杀区场次 productId = 克隆商品 id", mine and mine["productId"] == created_flash_pid,
          f"productId={mine['productId'] if mine else '-'}")
    check("C2 秒杀区透出原商品 id（sourceProductId）", mine and mine.get("sourceProductId") == source_pid,
          f"sourceProductId={mine.get('sourceProductId') if mine else '-'}")
    check("C3 ⭐ 没有任何场次指向原商品（原商品不再带秒杀标记）",
          not any(s["productId"] == source_pid for s in public_sales), "原商品无秒杀场次")

    # ============ D. 下单：只动克隆品库存 + 名额 ============
    call("POST", "/cart/items", {"productId": created_flash_pid, "quantity": 3}, tok)
    cart = call("GET", "/cart", None, tok)
    line = next((i for i in cart.get("items", []) if i["productId"] == created_flash_pid), None)
    check("D1 克隆商品可正常加入购物车，且享秒杀价",
          line and line.get("flashQty") == 3,
          f"flashQty={line.get('flashQty') if line else '-'} price={line.get('productPrice') if line else '-'}")

    order = call("POST", "/orders", {"cartItemIds": [line["id"]], "addressId": address_id,
                                     "fulfillmentType": "DELIVERY"}, tok)
    created_orders.append(order["id"])
    items = call("GET", f"/orders/{order['id']}", None, tok).get("items", [])
    check("D2 订单行挂上了秒杀场次（按秒杀价成交）",
          items and items[0].get("flashSaleId") == created_sale,
          f"flashSaleId={items[0].get('flashSaleId') if items else '-'}")

    fstock_after = int(sql(f"SELECT stock FROM {DB}.product WHERE id={created_flash_pid}"))
    sold_after = int(sql(f"SELECT sold_quota FROM {DB}.flash_sale WHERE id={created_sale}"))
    src_stock_after = int(sql(f"SELECT stock FROM {DB}.product WHERE id={source_pid}"))
    check("D3 ⭐ 克隆商品库存 10 → 7（下单扣的是它）", fstock_after == 7, f"stock={fstock_after}")
    check("D4 秒杀名额已占 3", sold_after == 3, f"sold_quota={sold_after}")
    check("D5 ⭐ 原商品库存纹丝不动（%d → %d）" % (src_stock_before, src_stock_after),
          src_stock_after == src_stock_before, f"原商品库存={src_stock_after}")

    # ============ E. 取消订单：库存与名额一并回退 ============
    call("POST", f"/orders/{order['id']}/cancel", None, tok)
    fstock_back = int(sql(f"SELECT stock FROM {DB}.product WHERE id={created_flash_pid}"))
    sold_back = int(sql(f"SELECT sold_quota FROM {DB}.flash_sale WHERE id={created_sale}"))
    src_stock_back = int(sql(f"SELECT stock FROM {DB}.product WHERE id={source_pid}"))
    check("E1 ⭐ 取消后克隆商品库存回到 10", fstock_back == 10, f"stock={fstock_back}")
    check("E2 取消后秒杀名额回到 0", sold_back == 0, f"sold_quota={sold_back}")
    check("E3 取消后原商品库存仍为 %d" % src_stock_before, src_stock_back == src_stock_before,
          f"原商品库存={src_stock_back}")

    # ============ F. 删除场次 → 克隆商品一并软删 ============
    call("DELETE", f"/admin/flash-sales/{created_sale}", None, admin_tok)
    fdel = sql(f"SELECT deleted FROM {DB}.product WHERE id={created_flash_pid}")
    check("F1 ⭐ 删除场次后，克隆商品一并下架（deleted=1）", fdel == "1", f"deleted={fdel}")
    src_del = sql(f"SELECT deleted FROM {DB}.product WHERE id={source_pid}")
    check("F2 原商品不受影响（仍 deleted=0）", src_del == "0", f"deleted={src_del}")

finally:
    try:
        # 归还订单占用的库存（取消过的订单本已归还，这里兜底）
        olist = ",".join(str(i) for i in created_orders) or "NULL"
        if created_orders:
            sql(f"UPDATE {DB}.product p JOIN ("
                f"SELECT oi.product_id AS pid, SUM(oi.quantity) AS q FROM {DB}.order_item oi "
                f"JOIN {DB}.orders o ON o.id=oi.order_id WHERE oi.order_id IN ({olist}) "
                f"AND o.status NOT IN ('CANCELED','CLOSED') GROUP BY oi.product_id) t ON t.pid=p.id "
                f"SET p.stock=p.stock+t.q, p.sales=GREATEST(p.sales-t.q,0)")
            sql(f"UPDATE {DB}.orders SET user_coupon_id=NULL WHERE id IN ({olist})")
        ids = [i for i in (uid, admin_id) if i is not None]
        idlist = ",".join(str(i) for i in ids) or "NULL"
        for t in ("product_review", "user_message", "wallet_transaction", "point_ledger",
                  "user_favorite", "price_alert", "cart_item", "user_address", "user_coupon"):
            sql(f"DELETE FROM {DB}.{t} WHERE user_id IN ({idlist})")
        if created_orders:
            for oid in created_orders:
                sql(f"DELETE FROM {DB}.order_item WHERE order_id={oid}")
                sql(f"DELETE FROM {DB}.payment_record WHERE order_id={oid}")
                sql(f"DELETE FROM {DB}.stock_log WHERE order_id={oid}")
            sql(f"DELETE FROM {DB}.orders WHERE id IN ({olist})")
        if created_sale:
            sql(f"DELETE FROM {DB}.flash_sale WHERE id={created_sale}")
        if created_flash_pid:
            # 克隆商品的规格 / 记录先清，再删商品本体（失败则退回软删）
            try:
                sql(f"DELETE FROM {DB}.product_sku WHERE product_id={created_flash_pid}")
                sql(f"DELETE FROM {DB}.stock_log WHERE product_id={created_flash_pid}")
                sql(f"DELETE FROM {DB}.product WHERE id={created_flash_pid}")
            except Exception:
                sql(f"UPDATE {DB}.product SET deleted=1, status='OFF_SALE' WHERE id={created_flash_pid}")
        sql(f"DELETE FROM {DB}.sys_user WHERE id IN ({idlist})")
        print("CLEANUP_OK")
    except Exception as exc:  # noqa: BLE001
        print("CLEANUP_FAIL:", exc)

passed = sum(1 for _, ok in results if ok)
print(f"\n==== {passed}/{len(results)} 通过 ====")
for name, ok in results:
    if not ok:
        print("  未通过: " + name)
