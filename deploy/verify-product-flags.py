"""商品「热门 / 新品」标记不被种子重置验证。

背景（2026-09-20 发现）：商品卡的「热」「新」角标由 `product.is_hot` / `is_new` 决定，
两者都能在后台「商品管理」里勾选。但它们原来**在种子的 ON DUPLICATE KEY UPDATE 回写列表里**，
而 DataSeeder 每次启动都跑种子 → **运营勾了「热门」，后端一重启就被写回种子值**（静默重置、等于白勾）。
修法：把 is_hot / is_new 从回写列表里摘掉（首次插入仍带种子初值），与公告那条同样思路。

覆盖：
  A. 结构断言：种子的 product 回写列表里**不再含** is_hot / is_new（防有人手滑加回去）
  B. 后台勾「热门」/「新品」→ DB 与公开接口立即反映
  C. ⭐ 重跑种子（等价于重启时 DataSeeder 的动作）→ 勾选**被保留**
  D. 同一次重跑里，其余列仍在自愈（stock 回到种子值）—— 证明没把整个自愈关掉
  E. 恢复原状 + 清理临时管理员 + 对账

⚠️ 注意：`updateProduct` 会**无条件删除再重建** images / skus / attributes，
所以 PUT 时必须把 GET 到的原值带回去（见 build_payload），否则会把商品资料清空。
"""
import json
import subprocess
import time
import urllib.error
import urllib.request
from _db import DBUSER, DBPASS

BASE = "http://localhost:8080/api"
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
SEED = r"D:\supermarket system\backend\src\main\resources\db\seed-data.sql"
DB = "supermarket_system"
STAMP = str(int(time.time()))

admin = "vpfadm_" + STAMP
PW = "Vpf12345"

HOT_ID = 65      # 鸡蛋：is_hot 原为 0（它也在热搜词里 —— 用户问过为什么没「热」标）
NEW_ID = 80      # 食用油：is_new 原为 0

admin_id = None
results = []
orig_hot = None
orig_new = None
seed_stock = None


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
        raise RuntimeError("%s %s -> %s %s" % (method, path, status, payload))
    return payload.get("data")


def sql(stmt):
    p = subprocess.run([MYSQL, "-u", DBUSER, "-p" + DBPASS, "--default-character-set=utf8mb4",
                        "-D", DB, "-N", "-B", "-e", stmt],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if p.returncode != 0:
        raise RuntimeError("SQL fail: " + p.stderr)
    return p.stdout.strip()


def replay_product_seed():
    """只重跑种子里那段 product 插入 —— 等价于后端重启时 DataSeeder 对它做的事。"""
    with open(SEED, encoding="utf-8") as fh:
        text = fh.read()
    start = text.index("INSERT INTO `product`")
    end = text.index(";", start) + 1
    p = subprocess.run([MYSQL, "-u", DBUSER, "-p" + DBPASS, "--default-character-set=utf8mb4",
                        "-D", DB, "-e", text[start:end]],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if p.returncode != 0:
        raise RuntimeError("seed(product) fail: " + p.stderr)


def build_payload(d, **overrides):
    """按 AdminProductUpdateRequest 的形状回传（images/skus/attributes 必须带上，见文件头 ⚠️）。"""
    payload = {
        "categoryId": d["categoryId"], "sku": d["sku"], "name": d["name"],
        "subtitle": d.get("subtitle"), "description": d.get("description"),
        "coverUrl": d.get("coverUrl"), "price": d["price"],
        "originalPrice": d.get("originalPrice"), "memberPrice": d.get("memberPrice"),
        "stock": d.get("stock"), "unit": d.get("unit") or "piece",
        "brand": d.get("brand"), "tags": d.get("tags"),
        "isHot": d.get("isHot"), "isNew": d.get("isNew"),
        "images": [{"url": x.get("url"), "sortNo": x.get("sortNo", 0)} for x in (d.get("images") or [])],
        "skus": [{"specJson": x.get("specJson") or "", "skuCode": x.get("skuCode") or "",
                  "image": x.get("image") or "", "sortNo": x.get("sortNo", 0)}
                 for x in (d.get("skus") or [])],
        "attributes": [{"attrName": x.get("attrName") or "", "attrValue": x.get("attrValue") or "",
                        "sortNo": x.get("sortNo", 0)}
                       for x in (d.get("attributes") or [])],
    }
    payload.update(overrides)
    return payload


def check(name, cond, detail=""):
    results.append((name, bool(cond)))
    print(("PASS  " if cond else "FAIL  ") + name + (("  | " + str(detail)) if detail else ""))


def flags(pid):
    row = sql("SELECT is_hot, is_new FROM %s.product WHERE id=%s" % (DB, pid)).split("\t")
    return int(row[0]), int(row[1])


def stock_of(pid):
    return int(sql("SELECT stock FROM %s.product WHERE id=%s" % (DB, pid)))


def api_product(pid):
    return call("GET", "/products/%s" % pid)


tok = None

try:
    # ---------- A. 结构断言（最便宜也最关键） ----------
    with open(SEED, encoding="utf-8") as fh:
        text = fh.read()
    start = text.index("INSERT INTO `product`")
    block = text[start:text.index(";", start)]
    update_part = block[block.index("ON DUPLICATE KEY UPDATE"):] if "ON DUPLICATE KEY UPDATE" in block else ""
    check("A1 ⭐ 种子的商品回写列表里已不含 is_hot（否则后台勾了重启就丢）",
          "is_hot=VALUES" not in update_part)
    check("A2 ⭐ 种子的商品回写列表里已不含 is_new", "is_new=VALUES" not in update_part)

    # ---------- 准备 ----------
    orig_hot = flags(HOT_ID)[0]
    orig_new = flags(NEW_ID)[1]
    seed_stock = stock_of(HOT_ID)

    tok = call("POST", "/auth/register",
               {"username": admin, "password": PW, "nickname": "商品标记管理员",
                "phone": "134" + STAMP[-8:]})["token"]
    admin_id = call("GET", "/auth/me", None, tok)["id"]
    sql("UPDATE %s.sys_user SET role='ADMIN' WHERE id=%s" % (DB, admin_id))
    tok = call("POST", "/auth/login", {"username": admin, "password": PW})["token"]

    # ---------- B. 后台勾选 → 立即反映 ----------
    d = call("GET", "/admin/products/%s" % HOT_ID, None, tok)
    call("PUT", "/admin/products/%s" % HOT_ID, build_payload(d, isHot=1, isNew=0), tok)
    check("B1 后台勾「热门」后 DB 里 is_hot=1", flags(HOT_ID)[0] == 1, flags(HOT_ID))
    check("B2 公开接口把这个商品透出为 isHot", api_product(HOT_ID).get("isHot") in (1, True),
          api_product(HOT_ID).get("isHot"))

    d2 = call("GET", "/admin/products/%s" % NEW_ID, None, tok)
    call("PUT", "/admin/products/%s" % NEW_ID, build_payload(d2, isHot=0, isNew=1), tok)
    check("B3 后台勾「新品」后 DB 里 is_new=1", flags(NEW_ID)[1] == 1, flags(NEW_ID))

    # ---------- C. ⭐ 重跑种子后仍保留（修复的核心） ----------
    replay_product_seed()
    check("C1 ⭐ 重跑种子后 is_hot 仍是 1（后台勾选没被写回）", flags(HOT_ID)[0] == 1, flags(HOT_ID))
    check("C2 ⭐ 重跑种子后 is_new 仍是 1", flags(NEW_ID)[1] == 1, flags(NEW_ID))
    check("C3 ⭐ 公开接口仍然反映这两个标记",
          api_product(HOT_ID).get("isHot") in (1, True) and api_product(NEW_ID).get("isNew") in (1, True))

    # ---------- D. 其余列没停止自愈 ----------
    sql("UPDATE %s.product SET stock=stock+7 WHERE id=%s" % (DB, HOT_ID))
    check("D1 前置：已把 stock 改脏（+7）", stock_of(HOT_ID) == seed_stock + 7,
          "%s -> %s" % (seed_stock, stock_of(HOT_ID)))
    replay_product_seed()
    check("D2 重跑种子后 stock 回到种子值（其余列照旧自愈，没把自愈整体关掉）",
          stock_of(HOT_ID) == seed_stock, "%s vs %s" % (stock_of(HOT_ID), seed_stock))
    check("D3 ⭐ 而 is_hot 依然是我们勾的 1（自愈与「保留运营标记」互不干扰）",
          flags(HOT_ID)[0] == 1, flags(HOT_ID))

    # ---------- E. 恢复原状 ----------
    call("PUT", "/admin/products/%s" % HOT_ID, build_payload(d, isHot=orig_hot), tok)
    call("PUT", "/admin/products/%s" % NEW_ID, build_payload(d2, isNew=orig_new), tok)
    check("E1 已还原鸡蛋的 is_hot", flags(HOT_ID)[0] == orig_hot, flags(HOT_ID))
    check("E2 已还原食用油的 is_new", flags(NEW_ID)[1] == orig_new, flags(NEW_ID))
    check("E3 商品资料未被清空（images/skus/attributes 仍与操作前一致）",
          len(api_product(HOT_ID).get("images") or []) == len(d.get("images") or [])
          and len(api_product(HOT_ID).get("skus") or []) == len(d.get("skus") or []),
          "images=%s skus=%s" % (len(api_product(HOT_ID).get("images") or []), len(api_product(HOT_ID).get("skus") or [])))

finally:
    try:
        if orig_hot is not None:
            sql("UPDATE %s.product SET is_hot=%s WHERE id=%s" % (DB, orig_hot, HOT_ID))
        if orig_new is not None:
            sql("UPDATE %s.product SET is_new=%s WHERE id=%s" % (DB, orig_new, NEW_ID))
        if seed_stock is not None:
            sql("UPDATE %s.product SET stock=%s WHERE id=%s" % (DB, seed_stock, HOT_ID))
        if admin_id is not None:
            for t in ("product_review", "user_message", "wallet_transaction", "point_ledger",
                      "user_favorite", "price_alert", "cart_item", "user_address", "user_coupon"):
                sql("DELETE FROM %s.%s WHERE user_id=%s" % (DB, t, admin_id))
            sql("DELETE FROM %s.sys_user WHERE id=%s" % (DB, admin_id))
        print("CLEANUP_OK")
    except Exception as exc:  # noqa: BLE001
        print("CLEANUP_FAIL:", exc)

passed = sum(1 for _, ok in results if ok)
print("\n==== %d/%d 通过 ====" % (passed, len(results)))
for name, ok in results:
    if not ok:
        print("  未通过: " + name)
