"""首页头部「热搜」词条可后台管验证。

背景：那排词（纯牛奶/五常大米/食用油/抽纸/鸡蛋）原本是前端 App.vue 里写死的 5 条 ——
既没有「怎么才算热」的规则，后台也改不了。现改为：
  · 前台数据源 = GET /hot-searches（只读启用项，按 sort_order 升序）
  · 后台 = /admin/hot-searches 增删改 + 启停，「热搜词」菜单
  · keyword = 点击后实际搜索的词（走 /shop?kw=）；label = 展示文案，留空回落成 keyword

覆盖：
  A. 公开接口：只返回启用的、按排序升序、label 回落正确
  B. 权限：非管理员访问后台接口被拒
  C. 后台新增/编辑 → 公开接口立即反映；排序生效
  D. 停用 → 前台消失（后台仍可见）；再启用 → 回来
  E. ⭐ 重跑种子语句：改过的值不被重置；⭐ 软删的种子词不会被种子「复活」
  F. 重复搜索词被拒（400）
  G. 恢复原状 + 清理临时管理员 + 对账

自建测试管理员，finally 清库（先删 user 级流水再删 sys_user）。
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

admin = "vhsadm_" + STAMP
guest = "vhsusr_" + STAMP
PW = "Vhs12345"

SEED_IDS = (1, 2, 3, 4, 5)          # 种子里的默认热搜词
SEED_ID_EDITED = 1                  # 改 label 用它验证"重跑种子不被重置"
SEED_ID_DELETED = 5                 # 软删它验证"删了不复活"
ORIG_LABEL_1 = "纯牛奶"

NEW_KEYWORD = "验证词" + STAMP[-6:]
NEW_LABEL = "【验证】热搜标签"

admin_id = None
guest_id = None
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
    p = subprocess.run([MYSQL, "-u", DBUSER, "-p" + DBPASS, "--default-character-set=utf8mb4",
                        "-D", DB, "-N", "-B", "-e", stmt],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if p.returncode != 0:
        raise RuntimeError("SQL fail: " + p.stderr)
    return p.stdout.strip()


def replay_hot_search_seed():
    """只重跑种子里那段 hot_search 插入 —— 等价于后端重启时 DataSeeder 对它做的事。"""
    with open(SEED, encoding="utf-8") as fh:
        text = fh.read()
    start = text.index("INSERT IGNORE INTO `hot_search`")
    end = text.index(";", start) + 1
    p = subprocess.run([MYSQL, "-u", DBUSER, "-p" + DBPASS, "--default-character-set=utf8mb4",
                        "-D", DB, "-e", text[start:end]],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if p.returncode != 0:
        raise RuntimeError("seed(hot_search) fail: " + p.stderr)


def check(name, cond, detail=""):
    results.append((name, bool(cond)))
    print(("PASS  " if cond else "FAIL  ") + name + (("  | " + str(detail)) if detail else ""))


def public():
    return call("GET", "/hot-searches") or []


def public_keywords():
    return [h.get("keyword") for h in public()]


def db_label(hid):
    return sql("SELECT IFNULL(label,'<NULL>') FROM %s.hot_search WHERE id=%s" % (DB, hid))


def db_deleted(hid):
    return sql("SELECT deleted FROM %s.hot_search WHERE id=%s" % (DB, hid))


tok = None
guest_tok = None

try:
    # ---------- 准备：临时管理员 + 普通用户 ----------
    tok = call("POST", "/auth/register",
               {"username": admin, "password": PW, "nickname": "热搜管理员",
                "phone": "135" + STAMP[-8:]})["token"]
    admin_id = call("GET", "/auth/me", None, tok)["id"]
    sql("UPDATE %s.sys_user SET role='ADMIN' WHERE id=%s" % (DB, admin_id))
    tok = call("POST", "/auth/login", {"username": admin, "password": PW})["token"]

    guest_tok = call("POST", "/auth/register",
                     {"username": guest, "password": PW, "nickname": "热搜普通用户",
                      "phone": "137" + STAMP[-8:]})["token"]
    guest_id = call("GET", "/auth/me", None, guest_tok)["id"]

    # ---------- A. 公开接口 ----------
    words = public()
    check("A1 公开接口 /hot-searches 返回配置的热搜词", len(words) >= len(SEED_IDS),
          "%s 条: %s" % (len(words), public_keywords()))

    fallback = [h for h in words if int(h["id"]) in (3, 4, 5)]
    check("A2 ⭐ label 留空时回落成 keyword（DB 里这 3 条 label 是 NULL）",
          fallback and all(h["label"] == h["keyword"] for h in fallback),
          [(h["keyword"], h["label"]) for h in fallback])

    sorts = [int(h["sortOrder"]) for h in words]
    check("A3 按排序值升序返回（后台改排序即改前台顺序）", sorts == sorted(sorts), sorts)

    check("A4 公开接口只含启用项（enabled 全为 1）",
          all(int(h["enabled"]) == 1 for h in words), [h["enabled"] for h in words])

    # ---------- B. 权限 ----------
    status, _ = request("GET", "/admin/hot-searches", None, guest_tok)
    check("B1 普通用户访问后台热搜接口被拒（403）", status == 403, "HTTP %s" % status)
    status, _ = request("GET", "/admin/hot-searches")
    check("B2 未登录访问后台热搜接口被拒", status in (401, 403), "HTTP %s" % status)

    # ---------- C. 后台增改 → 立即生效 ----------
    created = call("POST", "/admin/hot-searches",
                   {"keyword": NEW_KEYWORD, "label": NEW_LABEL, "sortOrder": 99, "enabled": True}, tok)
    created_ids.append(created["id"])
    check("C1 后台新增 → 公开接口立即可见（无需重启），label 用配置的展示文案",
          any(h["keyword"] == NEW_KEYWORD and h["label"] == NEW_LABEL for h in public()),
          public_keywords())

    call("PUT", "/admin/hot-searches/%s" % created["id"],
         {"keyword": NEW_KEYWORD, "label": NEW_LABEL + "改", "sortOrder": 99, "enabled": True}, tok)
    check("C2 后台改展示文案 → 公开接口立即反映",
          any(h["keyword"] == NEW_KEYWORD and h["label"] == NEW_LABEL + "改" for h in public()))

    call("PUT", "/admin/hot-searches/%s" % created["id"],
         {"keyword": NEW_KEYWORD, "label": NEW_LABEL + "改", "sortOrder": 0, "enabled": True}, tok)
    check("C3 ⭐ 排序值改为 0 → 该词排到公开接口第一位（顺序可控）",
          public() and public()[0]["keyword"] == NEW_KEYWORD, public_keywords())

    # ---------- D. 启停 ----------
    call("PUT", "/admin/hot-searches/%s" % created["id"],
         {"keyword": NEW_KEYWORD, "label": NEW_LABEL, "sortOrder": 0, "enabled": False}, tok)
    check("D1 停用后前台不再返回它", NEW_KEYWORD not in public_keywords(), public_keywords())
    check("D2 停用后后台列表仍能看到（便于再启用）",
          any(int(h["id"]) == created["id"] for h in call("GET", "/admin/hot-searches", None, tok)))

    call("PUT", "/admin/hot-searches/%s" % created["id"],
         {"keyword": NEW_KEYWORD, "label": NEW_LABEL, "sortOrder": 0, "enabled": True}, tok)
    check("D3 重新启用后前台又可见", NEW_KEYWORD in public_keywords())

    # ---------- E. ⭐ 种子不会覆盖/复活运营改动 ----------
    call("PUT", "/admin/hot-searches/%s" % SEED_ID_EDITED,
         {"keyword": "牛奶", "label": "【验证】纯牛奶", "sortOrder": 1, "enabled": True}, tok)
    replay_hot_search_seed()
    check("E1 ⭐ 重跑种子后，后台改过的展示文案没被重置（INSERT IGNORE 生效）",
          db_label(SEED_ID_EDITED) == "【验证】纯牛奶"
          and any(h["id"] == SEED_ID_EDITED and h["label"] == "【验证】纯牛奶" for h in public()),
          db_label(SEED_ID_EDITED))

    call("DELETE", "/admin/hot-searches/%s" % SEED_ID_DELETED, None, tok)
    check("E2 删除种子词后前台立即消失", SEED_ID_DELETED not in [h["id"] for h in public()],
          public_keywords())
    replay_hot_search_seed()
    check("E3 ⭐ 重跑种子后它也没被「复活」（软删挡住 INSERT IGNORE —— 物理删就会复活）",
          db_deleted(SEED_ID_DELETED) == "1"
          and SEED_ID_DELETED not in [h["id"] for h in public()],
          "deleted=%s" % db_deleted(SEED_ID_DELETED))

    # ---------- F. 重复词被拒 ----------
    status, payload = request("POST", "/admin/hot-searches",
                              {"keyword": "牛奶", "label": "重复测试", "sortOrder": 9, "enabled": True}, tok)
    msg = json.dumps(payload, ensure_ascii=False)
    check("F1 重复搜索词被拒（400 且点明已存在）", status == 400 and "已存在" in msg,
          "HTTP %s | %s" % (status, msg[:80]))

    # ---------- G. 恢复原状 ----------
    call("PUT", "/admin/hot-searches/%s" % SEED_ID_EDITED,
         {"keyword": "牛奶", "label": ORIG_LABEL_1, "sortOrder": 1, "enabled": True}, tok)
    check("G1 已恢复种子词 id=1 的展示文案", db_label(SEED_ID_EDITED) == ORIG_LABEL_1,
          db_label(SEED_ID_EDITED))
    call("DELETE", "/admin/hot-searches/%s" % created["id"], None, tok)
    created_ids.remove(created["id"])
    check("G2 验证用的新增词已删除", NEW_KEYWORD not in public_keywords(), public_keywords())

finally:
    try:
        # 恢复种子词的原始状态（软删/改文案都还原），并清掉验证残留行
        sql("UPDATE {db}.hot_search SET deleted=0, enabled=1 WHERE id IN ({ids})"
            .format(db=DB, ids=",".join(str(i) for i in SEED_IDS)))
        sql("UPDATE {db}.hot_search SET label='{lb}' WHERE id={i}"
            .format(db=DB, lb=ORIG_LABEL_1, i=SEED_ID_EDITED))
        sql("DELETE FROM {db}.hot_search WHERE keyword LIKE '验证词%' OR IFNULL(label,'') LIKE '【验证】%'"
            .format(db=DB))
        for cid in created_ids:
            sql("DELETE FROM %s.hot_search WHERE id=%s" % (DB, cid))
        if admin_id is not None:
            for t in ("product_review", "user_message", "wallet_transaction", "point_ledger",
                      "user_favorite", "price_alert", "cart_item", "user_address", "user_coupon"):
                sql("DELETE FROM %s.%s WHERE user_id=%s" % (DB, t, admin_id))
            sql("DELETE FROM %s.sys_user WHERE id=%s" % (DB, admin_id))
        if guest_id is not None:
            for t in ("product_review", "user_message", "wallet_transaction", "point_ledger",
                      "user_favorite", "price_alert", "cart_item", "user_address", "user_coupon"):
                sql("DELETE FROM %s.%s WHERE user_id=%s" % (DB, t, guest_id))
            sql("DELETE FROM %s.sys_user WHERE id=%s" % (DB, guest_id))
        print("CLEANUP_OK")
    except Exception as exc:  # noqa: BLE001
        print("CLEANUP_FAIL:", exc)

passed = sum(1 for _, ok in results if ok)
print("\n==== %d/%d 通过 ====" % (passed, len(results)))
for name, ok in results:
    if not ok:
        print("  未通过: " + name)
