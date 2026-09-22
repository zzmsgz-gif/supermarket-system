"""验证：管理员重置过密码的账号在**后端**也被拦住（PROGRESS 未决③）。

背景：这个功能原先只有前端拦截（登录后弹一个不可关闭的改密弹窗）。绕过前端直接调 API 就能
不改成正常用 —— 安全要求等于没落地。本脚本验证新增的 `MustChangePasswordFilter`：

  A. 置 `must_change_password=1` 后：读接口 / 写接口 / 列表接口一律 403 且 `code=40302`
  B. 白名单仍放行：`GET /auth/me`（前端要靠它渲染并知道待改密状态）、`POST /auth/change-password`
  C. ⭐ 改密后**不重新登录**就恢复可用 —— 证明判定读的是**每次现查的库值**，不是 JWT 里的快照
     （若哪天有人改成读 JWT 声明，这条会立刻失败：旧 token 里 mcp 还是 1）
  D. 没误伤：未置标记的普通用户照常、管理员照常、未登录请求行为不变（公开 200 / 私有 401）
  E. 自清：删临时账号（按 CLEANUP.md 顺序），跑完核对零残留

用法：`python deploy/verify-force-change-password.py`（后端在 8080 跑着）
"""
import atexit
import json
import time
import urllib.error
import urllib.request

from _admin_token import temp_admin
from _db import DB, run_sql

BASE = "http://localhost:8080/api"
STAMP = str(int(time.time()))
BUYER = "vfc_" + STAMP
OTHER = "vfcother_" + STAMP
PW_INIT = "VfcInit12345"
PW_NEW = "VfcNew12345"
MUST_CHANGE_CODE = 40302

results = []
uid = None
other_uid = None


def check(name, ok, detail=""):
    results.append((name, ok))
    print(("PASS " if ok else "FAIL ") + name + (" :: " + str(detail) if detail != "" else ""))


def req(method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    r = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(r, timeout=15) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}


def register(username, nickname):
    phone = "13" + str(int(time.time() * 1000))[-9:]
    st, body = req("POST", "/auth/register",
                   body={"username": username, "password": PW_INIT, "phone": phone, "nickname": nickname})
    assert st == 200 and body.get("code") == 0, f"register {username} failed: {st} {body}"
    uid_ = int(run_sql(f"SELECT id FROM sys_user WHERE username='{username}'").split()[0])
    return uid_, body["data"]["token"]


def cleanup():
    """按 CLEANUP.md 的顺序删测试账号（先断 stock_log.operator_id，再删用户级流水）。"""
    for ids in [(uid, other_uid)]:
        real = [i for i in ids if i]
        if not real:
            continue
        idlist = ",".join(str(i) for i in real)
        try:
            run_sql(f"UPDATE stock_log SET operator_id=NULL WHERE operator_id IN ({idlist})")
            for t in ("product_review", "wallet_transaction", "user_message", "point_ledger",
                      "user_favorite", "price_alert", "cart_item", "user_address", "user_coupon"):
                run_sql(f"DELETE FROM {t} WHERE user_id IN ({idlist})")
            run_sql(f"UPDATE orders SET user_coupon_id=NULL WHERE user_id IN ({idlist})")
            run_sql(f"DELETE FROM orders WHERE user_id IN ({idlist})")
            run_sql(f"DELETE FROM sys_user WHERE id IN ({idlist})")
        except Exception as e:
            print(f"[cleanup] ⚠️ 按 id 清理失败：{e}")
    # 兜底：按用户名删（拿到 uid 之前中断也不会漏）。
    # ⚠️ 这里**必须**把用户级流水也带上：注册会自动发一张新人券（app.new-user-coupon-id），
    #    只删 sys_user 会被 user_coupon 的外键挡住 → 静默残留（本脚本第一次报错中断时就留下了一个）。
    for name in (BUYER, OTHER):
        try:
            sub = f"(SELECT id FROM (SELECT id FROM sys_user WHERE username='{name}') x)"
            run_sql(f"UPDATE stock_log SET operator_id=NULL WHERE operator_id IN {sub}")
            for t in ("product_review", "wallet_transaction", "user_message", "point_ledger",
                      "user_favorite", "price_alert", "cart_item", "user_address", "user_coupon"):
                run_sql(f"DELETE FROM {t} WHERE user_id IN {sub}")
            run_sql(f"UPDATE orders SET user_coupon_id=NULL WHERE user_id IN {sub}")
            run_sql(f"DELETE FROM orders WHERE user_id IN {sub}")
            run_sql(f"DELETE FROM sys_user WHERE username='{name}'")
        except Exception:
            pass


atexit.register(cleanup)

print(f"=== 强制改密后端拦截验证（{STAMP}）===")

uid, tok = register(BUYER, "待改密测试买家")
other_uid, other_tok = register(OTHER, "普通测试买家")
print(f"临时账号：{BUYER}(id={uid}) / {OTHER}(id={other_uid})\n")

# ---------- 0. 基线：置标记之前一切正常 ----------
st, _ = req("GET", "/cart", tok)
check("A0 置标记前 GET /cart 正常", st == 200, f"HTTP {st}")
st, _ = req("GET", "/cart", other_tok)
check("D0 另一个普通用户 GET /cart 正常", st == 200, f"HTTP {st}")

# ---------- 1. 模拟管理员重置密码：置 must_change_password=1 ----------
run_sql(f"UPDATE sys_user SET must_change_password=1 WHERE id={uid}")
flag = run_sql(f"SELECT must_change_password FROM sys_user WHERE id={uid}").split()[-1]
check("A1 已置 must_change_password=1", flag == "1", f"db={flag}")

# ---------- 2. 被拦：读 / 写 / 列表 ----------
st, body = req("GET", "/cart", tok)
check("A2 待改密时 GET /cart 被拦(403)", st == 403, f"HTTP {st}")
check("A3 拦截响应带 code=40302", body.get("code") == MUST_CHANGE_CODE, f"code={body.get('code')}")

st, body = req("GET", "/orders", tok)
check("A4 待改密时 GET /orders 被拦(403)", st == 403 and body.get("code") == MUST_CHANGE_CODE, f"HTTP {st}")

st, body = req("POST", "/cart/items", tok, {"productId": 1, "quantity": 1})
check("A5 待改密时写接口 POST /cart/items 被拦", st == 403 and body.get("code") == MUST_CHANGE_CODE, f"HTTP {st}")

st, _ = req("GET", "/messages", tok)
check("A6 待改密时 GET /messages 被拦", st == 403, f"HTTP {st}")

# ---------- 3. 白名单放行 ----------
st, body = req("GET", "/auth/me", tok)
check("B1 白名单 GET /auth/me 放行(200)", st == 200, f"HTTP {st}")
check("B2 /auth/me 正确回报 mustChangePassword=true",
      bool((body.get("data") or {}).get("mustChangePassword")) is True,
      f"取值={(body.get('data') or {}).get('mustChangePassword')}")

st, body = req("POST", "/auth/change-password", tok,
               {"currentPassword": PW_INIT, "newPassword": PW_NEW, "confirmPassword": PW_NEW})
check("B3 白名单 POST /auth/change-password 放行(200)", st == 200 and body.get("code") == 0, f"HTTP {st} {body}")

# ---------- 4. ⭐ 改密后立刻恢复（同一 token，不重新登录）----------
st, _ = req("GET", "/cart", tok)
check("C1 改密后**同一 token** 立刻恢复可用", st == 200, f"HTTP {st}")
st, body = req("GET", "/auth/me", tok)
check("C2 标记已清除(mustChangePassword=false)",
      (body.get("data") or {}).get("mustChangePassword") in (False, 0, None),
      f"取值={(body.get('data') or {}).get('mustChangePassword')}")
st, _ = req("GET", "/cart", other_tok)
check("D1 期间另一个用户始终不受影响", st == 200, f"HTTP {st}")

# ---------- 5. 没误伤：管理员 + 未登录 ----------
with temp_admin() as adm:
    st, _ = req("GET", "/admin/stats/overview", adm.token)
    check("D2 管理员 token 正常访问 /admin/**", st == 200, f"HTTP {st}")

st, _ = req("GET", "/products?page=1&size=1")
check("D3 未登录访问公开接口仍 200", st == 200, f"HTTP {st}")
st, _ = req("GET", "/cart")
check("D4 未登录访问私有接口仍 401（未被改成 403）", st == 401, f"HTTP {st}")

# ---------- 6. 清理与残留核对 ----------
cleanup()
left = run_sql(f"SELECT COUNT(*) FROM sys_user WHERE username IN ('{BUYER}','{OTHER}')").split()[-1]
check("E1 临时账号已清理", left == "0", f"残留={left}")
left2 = run_sql("SELECT COUNT(*) FROM sys_user WHERE username LIKE 'vfc%'").split()[-1]
check("E2 无 vfc* 残留账号", left2 == "0", f"残留={left2}")

passed = sum(1 for _, ok in results if ok)
print(f"\n==== {passed}/{len(results)} 项通过 ====")
print("CLEANUP_OK" if left == "0" and left2 == "0" else "CLEANUP_FAILED")
raise SystemExit(0 if passed == len(results) else 1)
