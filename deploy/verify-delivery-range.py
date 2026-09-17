"""同城即时配送范围校验 —— 端到端验证（后端 API 层）。

模型：**可送达范围 = 所有「营业中」门店 service_areas 的并集**；门店未配置时回退为本店 city+district。
快递配送不受限制（全国可达），门店自提更不校验地址。

覆盖：
  A. 准备：买家 + 三个地址（深圳市/南山区 在范围内；汕头市/潮南区 在范围外；深圳/南山 无后缀写法）
     A2 前提断言：地址接口强制要求区县（所以「只有城市没有区县」的分支不存在，范围校验里也不需要写）
  B. 查询接口 GET /delivery-range
     B1 范围内地址 → deliverable=true
     B2 范围外地址 → deliverable=false 且回传当前覆盖范围
     B3 city 为空 → deliverable=false（不能因为参数缺失就放行）
     B4 行政后缀/normalize 容差：地址写「深圳」「南山」（无"市""区"）仍判定可送达
  C. 下单强制（真正的防线）
     C1 INSTANT + 范围内地址 → 下单成功
     C2 INSTANT + 范围外地址 → 409，且文案点名地址/覆盖范围/**并给出改用什么**（快递配送）
     C3 EXPRESS + 范围外地址 → 下单成功（快递不受限）
     C4 PICKUP → 下单成功（不校验地址）
  D. 范围随门店状态收缩/恢复
     D1 把覆盖南山区的门店停业 → 该区立即退出范围（查询与下单都被拦）
     D2 恢复营业 → 重新可送达
  E. 未登录查询被拒(401)

自建测试账号/地址/订单，finally 按外键顺序清理，并还原被本脚本改动的门店状态。
"""
import json
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://localhost:8080/api"
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
DBPASS = "zzmsgz"
STAMP = str(int(time.time()))
results = []

buyer = "dlr_" + STAMP
PW = "Dlrtest123"
phone = "133" + STAMP[-8:]
uid = None
created_orders = []
ORIGINAL_STORE_STATUS = {}
MAX_LOG_ID = None


def call(method, path, body=None, token=None):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Content-Type": "application/json",
                 **({"Authorization": "Bearer " + token} if token else {})},
        method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode()).get("data"), resp.status
    except urllib.error.HTTPError as err:
        raise RuntimeError(f"{method} {path} -> {err.code} {err.read().decode()}")


def call_status(method, path, body=None, token=None):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Content-Type": "application/json",
                 **({"Authorization": "Bearer " + token} if token else {})},
        method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as err:
        return err.code, err.read().decode()


def sql(stmt):
    p = subprocess.run([MYSQL, "-uroot", "-p" + DBPASS, "-D", "supermarket_system",
                        "--default-character-set=utf8mb4", "-N", "-B", "-e", stmt],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if p.returncode != 0:
        raise RuntimeError("SQL fail: " + p.stderr)
    return p.stdout.strip()


def scalar(stmt):
    out = sql(stmt)
    return out.splitlines()[0].strip() if out else ""


def check(name, cond, detail=""):
    results.append((name, bool(cond)))
    print(("PASS  " if cond else "FAIL  ") + name + (("  | " + str(detail)) if detail else ""))


def range_check(token, city, district):
    qs = urllib.parse.urlencode({"city": city, "district": district})
    return call("GET", f"/delivery-range?{qs}", None, token)[0]


def add_address(token, city, district):
    data, _ = call("POST", "/addresses", {"receiverName": "范围验证", "receiverPhone": phone,
                                          "province": "广东省", "city": city, "district": district,
                                          "detailAddress": "验证路 1 号", "isDefault": True}, token)
    return data["id"]


def place(token, address_id=None, fulfillment=None, store_id=None):
    """按当前购物车下单，返回 (status, body)。下单成功后清空购物车以便复用。"""
    cart, _ = call("GET", "/cart", None, token)
    body = {"cartItemIds": [i["id"] for i in cart["items"]]}
    if address_id is not None:
        body["addressId"] = address_id
    if fulfillment is not None:
        body["fulfillmentType"] = fulfillment
    if store_id is not None:
        body["pickupStoreId"] = store_id
    st, raw = call_status("POST", "/orders", body, token)
    if st == 200:
        created_orders.append(json.loads(raw)["data"]["id"])
    return st, raw


try:
    MAX_LOG_ID = int(scalar("SELECT COALESCE(MAX(id),0) FROM supermarket_system.stock_log") or 0)

    reg = call("POST", "/auth/register", {"username": buyer, "password": PW,
                                          "nickname": "配送范围验证", "phone": phone})[0]
    tok = reg["token"]
    uid = call("GET", "/auth/me", None, tok)[0]["id"]
    call("POST", "/wallet/recharges", {"amount": 2000}, tok)

    addr_in = add_address(tok, "深圳市", "南山区")
    addr_out = add_address(tok, "汕头市", "潮南区")
    addr_no_suffix = add_address(tok, "深圳", "南山")
    check("A1 三个地址创建成功", all(isinstance(i, int) for i in (addr_in, addr_out, addr_no_suffix)))

    # A2 地址接口把区县定为必填（AddressRequest @NotBlank）—— 范围校验因此**不需要**处理
    #    「只有城市没有区县」的分支（写过一个，但那是走不到的死代码，已删）。
    st, body = call_status("POST", "/addresses", {"receiverName": "范围验证", "receiverPhone": phone,
                                                  "province": "广东省", "city": "深圳市", "district": "",
                                                  "detailAddress": "验证路 2 号", "isDefault": False}, tok)
    check("A2 前提：地址接口强制要求区县（所以不存在只有城市的地址）", st == 400, f"{st} {body[:80]}")

    products = call("GET", "/products?page=1&size=50", None, tok)[0]["items"]
    prod = next(p for p in products if p["stock"] > 5)
    store_list = call("GET", "/stores", None, tok)[0]
    store = store_list[0]

    # ============ B. 查询接口 ============
    r = range_check(tok, "深圳市", "南山区")
    check("B1 范围内地址 → deliverable=true", r["deliverable"] is True, r)

    r = range_check(tok, "汕头市", "潮南区")
    check("B2 范围外地址 → deliverable=false 且回传覆盖范围",
          r["deliverable"] is False and "深圳" in (r.get("coverage") or ""), r)

    r = range_check(tok, "", "")
    check("B3 city 为空 → deliverable=false（缺参数不能当作放行）", r["deliverable"] is False, r)

    r = range_check(tok, "深圳", "南山")
    check("B4 normalize 容差：写「深圳」「南山」（无市/区后缀）仍可送达", r["deliverable"] is True, r)

    # ============ C. 下单强制 ============
    call("POST", "/cart/items", {"productId": prod["id"], "quantity": 1}, tok)
    st, body = place(tok, addr_in, "INSTANT")
    check("C1 INSTANT + 范围内地址 → 下单成功", st == 200, f"{st} {body[:80]}")

    call("POST", "/cart/items", {"productId": prod["id"], "quantity": 1}, tok)
    st, body = place(tok, addr_out, "INSTANT")
    ok = (st == 409 and "汕头" in body and "深圳" in body and "快递配送" in body)
    check("C2 INSTANT + 范围外地址 → 409，文案含地址/覆盖范围/可改用的方式", ok, f"{st} {body[:160]}")

    st, body = place(tok, addr_out, "EXPRESS")
    check("C3 EXPRESS + 范围外地址 → 下单成功（快递不受配送范围限制）", st == 200, f"{st} {body[:100]}")

    call("POST", "/cart/items", {"productId": prod["id"], "quantity": 1}, tok)
    st, body = place(tok, None, "PICKUP", store["id"])
    check("C4 PICKUP → 下单成功（自提不校验地址）", st == 200, f"{st} {body[:100]}")

    # ============ D. 范围随门店状态收缩/恢复 ============
    nan_store = next(s for s in store_list if "南山" in (s.get("serviceAreas") or "") or "南山" in (s.get("district") or ""))
    ORIGINAL_STORE_STATUS[nan_store["id"]] = nan_store["status"]
    sql(f"UPDATE supermarket_system.store SET status=0 WHERE id={nan_store['id']}")
    r = range_check(tok, "深圳市", "南山区")
    call("POST", "/cart/items", {"productId": prod["id"], "quantity": 1}, tok)
    st2, _ = place(tok, addr_in, "INSTANT")
    check("D1 覆盖该区的门店停业 → 查询变 false 且下单被拦",
          r["deliverable"] is False and st2 == 409, f"deliverable={r['deliverable']} 下单={st2}")

    sql(f"UPDATE supermarket_system.store SET status={ORIGINAL_STORE_STATUS[nan_store['id']]} WHERE id={nan_store['id']}")
    r = range_check(tok, "深圳市", "南山区")
    check("D2 门店恢复营业 → 重新可送达", r["deliverable"] is True, r)

    # ============ E. 鉴权 ============
    st, _ = call_status("GET", "/delivery-range?city=%E6%B7%B1%E5%9C%B3%E5%B8%82&district=%E5%8D%97%E5%B1%B1%E5%8C%BA")
    check("E1 未登录查询被拒(401)", st == 401, st)

finally:
    try:
        for sid, status in ORIGINAL_STORE_STATUS.items():
            sql(f"UPDATE supermarket_system.store SET status={status} WHERE id={sid}")
        if uid is not None:
            sql(f"UPDATE supermarket_system.orders SET user_coupon_id=NULL WHERE user_id={uid}")
            for t in ("product_review", "wallet_transaction", "user_message", "point_ledger",
                      "user_favorite", "price_alert", "cart_item", "user_address", "user_coupon"):
                sql(f"DELETE FROM supermarket_system.{t} WHERE user_id={uid}")
            sql(f"DELETE FROM supermarket_system.stock_log WHERE id > {MAX_LOG_ID}")
            sql(f"DELETE oi FROM supermarket_system.order_item oi JOIN supermarket_system.orders o "
                f"ON o.id=oi.order_id WHERE o.user_id={uid}")
            sql(f"DELETE pr FROM supermarket_system.payment_record pr JOIN supermarket_system.orders o "
                f"ON o.id=pr.order_id WHERE o.user_id={uid}")
            sql(f"DELETE FROM supermarket_system.orders WHERE user_id={uid}")
            sql(f"DELETE FROM supermarket_system.sys_user WHERE id={uid}")
            check("Z1 清理后无残留账号", scalar(f"SELECT COUNT(*) FROM supermarket_system.sys_user WHERE id={uid}") == "0")
            check("Z2 清理后无残留订单", scalar(f"SELECT COUNT(*) FROM supermarket_system.orders WHERE user_id={uid}") == "0")
            check("Z3 门店状态已还原",
                  all(scalar(f"SELECT status FROM supermarket_system.store WHERE id={sid}") == str(s)
                      for sid, s in ORIGINAL_STORE_STATUS.items()))
        print("CLEANUP_OK")
    except Exception as exc:  # noqa: BLE001
        print("CLEANUP_FAIL:", exc)

passed = sum(1 for _, ok in results if ok)
print(f"\n==== {passed}/{len(results)} 项通过 ====")
for name, ok in results:
    if not ok:
        print("  FAILED:", name)
