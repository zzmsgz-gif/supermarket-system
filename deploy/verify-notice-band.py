"""首页顶部「利益条」文案可后台管验证（归口「营销活动管理」）。

背景：左上角那句「新人首单立减 ¥20，再送 3 张满减券」原本是前端硬编码 —— 后台改不了、文案还写错
（实际是注册发 1 张「满10减10」券）。现统一到「营销活动管理」：
  · 利益条数据源 = GET /activities/active
  · type=PROMOTION 的「纯文案活动」→ 直接显示 name（只宣传、不参与计价）
  · 满减/折扣活动 → 仍显示「限时活动 …」
  · 同一内容不再出现在「商城公告」栏（那条重复公告已由 deploy/upgrade-activity-promo.sql 删除）

覆盖：
  A. 公开接口透出纯文案活动；threshold/discount 为空（不符合计价条件）；公告栏已无重复的新人公告
  B. 后台改 name → 公开接口立即反映（无需重启）
  C. ⭐ 重跑种子脚本里的活动语句 → 不被重置（该活动用的是独立 INSERT IGNORE，不在 ON DUPLICATE 里）
  D. ⭐ 纯文案活动不参与计价：购物车选的「最优活动」仍是满减/折扣，减免金额照常算出
  E. 停用 → 公开接口不再返回；新增/删除一条纯文案活动（可加新的顶栏文案）
  F. 恢复原状 + 清理临时管理员 + 对账

自建测试管理员，finally 清库（先删 user 级流水再删 sys_user）。
"""
import json
import subprocess
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
SEED = r"D:\supermarket system\backend\src\main\resources\db\seed-data.sql"
DB = "supermarket_system"
DBPASS = "zzmsgz"
STAMP = str(int(time.time()))

admin = "vbnadm_" + STAMP
PW = "Vbn12345"

PROMO_ID = 28              # 种子里的纯文案活动（新人福利，顶栏那条）
PROD_ID = 79               # 大米 39.9/袋；6 袋 = 239.4 > 满200 门槛，用来验证计价未受影响
NEW_NAME = "【验证】新人立减文案_" + STAMP
NEW_NAME2 = "【验证】新加的顶栏文案_" + STAMP

admin_id = None
created_ids = []
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
        raise RuntimeError("%s %s -> %s %s" % (method, path, status, payload))
    return payload.get("data")


def sql(stmt):
    p = subprocess.run([MYSQL, "-uroot", "-p" + DBPASS, "--default-character-set=utf8mb4",
                        "-D", DB, "-N", "-B", "-e", stmt],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if p.returncode != 0:
        raise RuntimeError("SQL fail: " + p.stderr)
    return p.stdout.strip()


def replay_promo_seed():
    """只重跑种子里那条 PROMOTION 活动的插入语句 —— 等价于后端重启时 DataSeeder 对它做的事。"""
    with open(SEED, encoding="utf-8") as fh:
        text = fh.read()
    start = text.index("INSERT IGNORE INTO `activity`")
    end = text.index(";", start) + 1
    p = subprocess.run([MYSQL, "-uroot", "-p" + DBPASS, "--default-character-set=utf8mb4",
                        "-D", DB, "-e", text[start:end]],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if p.returncode != 0:
        raise RuntimeError("seed(activity) fail: " + p.stderr)


def check(name, cond, detail=""):
    results.append((name, bool(cond)))
    print(("PASS  " if cond else "FAIL  ") + name + (("  | " + str(detail)) if detail else ""))


def active():
    return call("GET", "/activities/active") or []


def promos():
    return [a for a in active() if str(a.get("type") or "").upper() == "PROMOTION"]


def promo_names():
    return [a.get("name") for a in promos()]


def db_name():
    return sql("SELECT name FROM %s.activity WHERE id=%s" % (DB, PROMO_ID))


def db_status():
    return sql("SELECT status FROM %s.activity WHERE id=%s" % (DB, PROMO_ID))


orig = None
tok = None

try:
    # ---------- 准备：临时管理员 ----------
    tok = call("POST", "/auth/register",
               {"username": admin, "password": PW, "nickname": "利益条管理员",
                "phone": "136" + STAMP[-8:]})["token"]
    admin_id = call("GET", "/auth/me", None, tok)["id"]
    sql("UPDATE %s.sys_user SET role='ADMIN' WHERE id=%s" % (DB, admin_id))
    tok = call("POST", "/auth/login", {"username": admin, "password": PW})["token"]

    # ---------- A. 数据源与现状 ----------
    check("A1 公开接口 /activities/active 透出「纯文案活动」(type=PROMOTION)",
          len(promos()) >= 1, promo_names())

    promo = next((a for a in promos() if a["id"] == PROMO_ID), None)
    check("A2 目标纯文案活动存在（种子 id=%s）" % PROMO_ID, promo is not None,
          promo and promo.get("name"))
    if promo is None:
        raise RuntimeError("纯文案活动 id=%s 不存在，无法继续" % PROMO_ID)

    check("A3 ⭐ 纯文案活动没有门槛/优惠值（结构上就不满足计价条件）",
          promo.get("threshold") is None and promo.get("discount") is None,
          "threshold=%s discount=%s" % (promo.get("threshold"), promo.get("discount")))

    check("A4 顶栏文案含「新人」福利（替代了原硬编码那句）",
          any("新人" in (n or "") for n in promo_names()), promo_names())

    dup = int(sql("SELECT COUNT(*) FROM {db}.announcement WHERE deleted=0 AND type='PROMOTION' "
                  "AND title LIKE '新人%'".format(db=DB)))
    check("A5 ⭐ 商城公告栏里已没有重复的新人促销公告（不再两处显示同一内容）",
          dup == 0, "%s 条" % dup)

    # ---------- B. 后台改文案 → 立即生效 ----------
    orig = promo
    payload = {"name": NEW_NAME, "type": "PROMOTION", "scope": "ALL",
               "startTime": orig["startTime"], "endTime": orig["endTime"],
               "status": 1, "priority": int(orig.get("priority") or 0)}
    call("PUT", "/admin/activities/%s" % PROMO_ID, payload, tok)
    check("B1 后台改 name 后公开接口立即反映（无需重启）",
          NEW_NAME in promo_names(), promo_names())

    # ---------- C. ⭐ 重跑种子不被重置 ----------
    replay_promo_seed()
    check("C1 ⭐ 重跑种子活动语句后 name 未被重置（独立 INSERT IGNORE 生效）",
          NEW_NAME in promo_names(), promo_names())
    check("C2 ⭐ 库内 name 也是新值（排除接口缓存造成的假通过）",
          db_name() == NEW_NAME, db_name())

    # ---------- D. ⭐ 计价不受影响 ----------
    call("POST", "/cart/items", {"productId": PROD_ID, "quantity": 6}, tok)
    cart = call("GET", "/cart", None, tok)
    check("D1 ⭐ 纯文案活动不会被选为「最优活动」（购物车选中的仍是满减/折扣）",
          cart.get("activityName") != NEW_NAME and cart.get("activityName") is not None,
          "activityName=%s" % cart.get("activityName"))
    check("D2 满减/折扣照常算出减免金额（计价链路未受影响）",
          float(cart.get("activityDiscount") or 0) > 0, "discount=%s" % cart.get("activityDiscount"))

    # ---------- E. 停用 / 新增 / 删除 ----------
    call("PATCH", "/admin/activities/%s/status" % PROMO_ID, {"status": 0}, tok)
    check("E1 停用后公开接口不再返回它（顶栏文案可下线）", NEW_NAME not in promo_names(), promo_names())
    call("PATCH", "/admin/activities/%s/status" % PROMO_ID, {"status": 1}, tok)

    created = call("POST", "/admin/activities",
                   {"name": NEW_NAME2, "type": "PROMOTION", "scope": "ALL",
                    "startTime": orig["startTime"], "endTime": orig["endTime"],
                    "status": 1, "priority": 0}, tok)
    created_ids.append(created["id"])
    check("E2 后台新增一条纯文案活动 → 立即出现在公开接口（可加新的顶栏文案）",
          NEW_NAME2 in promo_names(), promo_names())
    call("DELETE", "/admin/activities/%s" % created["id"], None, tok)
    created_ids.remove(created["id"])
    check("E3 删除后从公开接口消失", NEW_NAME2 not in promo_names(), promo_names())

    # ---------- F. 恢复原状 ----------
    call("PUT", "/admin/activities/%s" % PROMO_ID,
         {"name": orig["name"], "type": "PROMOTION", "scope": "ALL",
          "startTime": orig["startTime"], "endTime": orig["endTime"],
          "status": 1, "priority": int(orig.get("priority") or 0)}, tok)
    check("F1 已恢复原文案", db_name() == orig["name"], db_name())
    check("F2 已恢复启用状态", db_status() == "1", db_status())

    # ---------- G. 对账 ----------
    leftover = int(sql("SELECT COUNT(*) FROM {db}.activity WHERE name LIKE '【验证】%' AND deleted=0"
                       .format(db=DB)))
    check("G1 无验证残留活动", leftover == 0, "%s 条" % leftover)

finally:
    try:
        # 活动用删除（软删）；硬删掉验证残留行
        sql("DELETE FROM {db}.activity WHERE name LIKE '【验证】%'".format(db=DB))
        for cid in created_ids:
            sql("DELETE FROM %s.activity WHERE id=%s" % (DB, cid))
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
