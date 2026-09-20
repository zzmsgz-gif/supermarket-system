"""首页顶部「利益条」文案后台可管验证。

背景：左上角那句「新人首单立减 ¥20，再送 3 张满减券」原本是前端硬编码 —— 后台改不了，
且文案与真实福利脱节（实际是注册发 1 张「新人专享券 满10减10」，不是 ¥20 也不是 3 张）。
本次改为：利益条 = 进行中的营销活动 + 后台「公告管理」里 type=促销(PROMOTION) 的启用公告标题。

⚠️ 关键前提：种子脚本对 announcement 必须用 INSERT IGNORE，
否则 DataSeeder 每次启动都会把后台改的文案重置回种子值 → 功能等于白做。本脚本专门钉住这一条。

覆盖：
  A. 公开接口透出 type；至少一条「促销」公告（顶栏有内容）；目标公告存在
  B. 后台改标题 → 公开接口立即反映（无需重启）
  C. ⭐ 改完重跑种子脚本（等价于 DataSeeder 在启动时做的事）→ 标题/启用状态都不被重置
  D. 停用 → 公开接口不再返回（顶栏文案可下线），且重跑种子也不会被复活
  E. 新增一条促销公告 → 出现；删除 → 消失（"可加新的一条"）
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

TARGET_ID = 1                       # 种子里的「新人专享 满10减10」(PROMOTION) —— 顶栏那条
NEW_TITLE = "【验证】新人注册立减_" + STAMP
NEW_TITLE2 = "【验证】新加的促销_" + STAMP

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


def replay_announcement_seed():
    """只重跑种子里的 announcement 语句 —— 等价于后端重启时 DataSeeder 对公告做的事，
    但不触碰商品库存/销量（避免验证脚本本身产生副作用）。"""
    with open(SEED, encoding="utf-8") as fh:
        text = fh.read()
    start = text.index("INSERT IGNORE INTO `announcement`")
    end = text.index(";", start) + 1
    stmt = text[start:end]
    p = subprocess.run([MYSQL, "-uroot", "-p" + DBPASS, "--default-character-set=utf8mb4",
                        "-D", DB, "-e", stmt],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if p.returncode != 0:
        raise RuntimeError("seed(announcement) fail: " + p.stderr)


def check(name, cond, detail=""):
    results.append((name, bool(cond)))
    print(("PASS  " if cond else "FAIL  ") + name + (("  | " + str(detail)) if detail else ""))


def promos():
    lst = call("GET", "/announcements") or []
    return [a for a in lst if str(a.get("type") or "").upper() == "PROMOTION"]


def promo_titles():
    return [a.get("title") for a in promos()]


def db_title():
    return sql("SELECT title FROM %s.announcement WHERE id=%s" % (DB, TARGET_ID))


def db_enabled():
    return sql("SELECT enabled FROM %s.announcement WHERE id=%s" % (DB, TARGET_ID))


def edit(title, enabled):
    call("PUT", "/admin/announcements/%s" % TARGET_ID,
         {"title": title, "content": orig["content"], "type": "PROMOTION",
          "sortOrder": orig.get("sortOrder") or 1, "enabled": enabled}, tok)


orig = None
tok = None

try:
    # ---------- 准备：临时管理员（注册后 SQL 提权） ----------
    tok = call("POST", "/auth/register",
               {"username": admin, "password": PW, "nickname": "公告带管理员",
                "phone": "136" + STAMP[-8:]})["token"]
    admin_id = call("GET", "/auth/me", None, tok)["id"]
    sql("UPDATE %s.sys_user SET role='ADMIN' WHERE id=%s" % (DB, admin_id))
    tok = call("POST", "/auth/login", {"username": admin, "password": PW})["token"]

    # ---------- A. 公开接口与现状 ----------
    public = call("GET", "/announcements") or []
    check("A1 公开接口 /announcements 透出 type（前端据此筛出促销类）",
          bool(public) and public[0].get("type") is not None,
          "type=%s" % (public[0].get("type") if public else "-"))
    check("A2 至少有一条「促销(PROMOTION)」公告 → 首页顶栏有内容可轮播",
          len(promos()) >= 1, "promos=%d" % len(promos()))
    check("A3 促销里含新人福利标题（已替代原硬编码文案）",
          any("新人" in (t or "") for t in promo_titles()), promo_titles())

    all_admin = call("GET", "/admin/announcements", None, tok) or []
    orig = next((a for a in all_admin if a["id"] == TARGET_ID), None)
    check("A4 目标公告存在（种子的新人福利）", orig is not None,
          "id=%s title=%s" % (TARGET_ID, orig and orig.get("title")))
    if orig is None:
        raise RuntimeError("目标公告 id=%s 不存在，无法继续" % TARGET_ID)

    # ---------- B. 后台改标题 → 立即生效 ----------
    edit(NEW_TITLE, True)
    check("B1 后台改标题后公开接口立即反映（无需重启）",
          NEW_TITLE in promo_titles(), promo_titles())

    # ---------- C. ⭐ 核心：重跑种子不被重置 ----------
    replay_announcement_seed()
    check("C1 ⭐ 重跑种子脚本后标题未被重置（INSERT IGNORE 生效，不是 ON DUPLICATE）",
          NEW_TITLE in promo_titles(), promo_titles())
    check("C2 ⭐ 库内标题也是新值（排除接口缓存造成的假通过）",
          db_title() == NEW_TITLE, db_title())

    # ---------- D. 停用 → 顶栏可下线 ----------
    edit(NEW_TITLE, False)
    check("D1 停用后公开接口不再返回它（顶栏文案可下线）",
          NEW_TITLE not in promo_titles(), promo_titles())
    replay_announcement_seed()
    check("D2 停用后重跑种子也不会被「复活」（enabled 不被种子回写）",
          db_enabled() == "0" and NEW_TITLE not in promo_titles(),
          "enabled=%s" % db_enabled())

    # ---------- E. 新增 / 删除 ----------
    created = call("POST", "/admin/announcements",
                   {"title": NEW_TITLE2, "content": "验证用：新增一条促销公告",
                    "type": "PROMOTION", "sortOrder": 9, "enabled": True}, tok)
    created_ids.append(created["id"])
    check("E1 后台新增一条促销公告 → 立即出现在公开接口（可加新的利益条文案）",
          NEW_TITLE2 in promo_titles(), promo_titles())
    call("DELETE", "/admin/announcements/%s" % created["id"], None, tok)
    created_ids.remove(created["id"])
    check("E2 删除后从公开接口消失", NEW_TITLE2 not in promo_titles(), promo_titles())

    # ---------- F. 恢复原状 ----------
    edit(orig["title"], int(orig.get("enabled") or 0) == 1)
    check("F1 已恢复原标题", db_title() == orig["title"], db_title())
    check("F2 已恢复原启用状态", db_enabled() == str(int(orig.get("enabled") or 0)),
          db_enabled())

    # ---------- G. 对账 ----------
    leaked = int(sql("SELECT COUNT(*) FROM {db}.announcement WHERE title LIKE '【验证】%' AND deleted=0"
                     .format(db=DB)))
    check("G1 无验证残留公告（未删除的）", leaked == 0, "%s 条" % leaked)

finally:
    try:
        # 硬删所有验证残留（后台删除是软删，会把行留在库里）
        sql("DELETE FROM {db}.announcement WHERE title LIKE '【验证】%'".format(db=DB))
        for cid in created_ids:
            sql("DELETE FROM %s.announcement WHERE id=%s" % (DB, cid))
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
