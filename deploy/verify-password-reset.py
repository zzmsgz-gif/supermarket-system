"""找回密码（忘记密码）端到端验证 —— 后端 API 层。

覆盖：
  A. 提交申请：账号存在时落库；重复提交幂等（不产生第二条 PENDING）
  B. 防账号枚举：不存在的账号返回**完全相同**的文案，且不落库
  C. 参数校验：空账号 / 空联系电话被拒(400)
  D. 权限：未登录 401；普通用户访问后台接口 403
  E. 后台列表（状态筛选）与待处理计数
  F. 管理员重置 → 拿到一次性临时密码（10 位、≠ 原密码），库里查不到明文
  G. 旧密码失效；临时密码可登录；登录响应带 mustChangePassword=true
  H. 用临时密码改密 → 标记清除、新密码可登录、临时密码失效
  I. 已处理的申请不能重复重置(400)
  J. 驳回流程，且驳回后可重新提交

自建测试账号，finally 中清理申请记录 + user 级流水 + sys_user。
⚠️ 不修改 admin(id=1) 的任何数据，只用它登录拿 token。
"""
import json
import subprocess
import time
import urllib.error
import urllib.request
from _db import DBUSER, DBPASS

BASE = "http://localhost:8080/api"
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
STAMP = str(int(time.time()))
results = []

user = "prv_" + STAMP
PW = "Origin123"
NEW = "Newpass456"
phone = "137" + STAMP[-8:]
contact = "13800001111"
ghost_user = "prv_ghost_" + STAMP
uid = None


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
    """返回 (status_code, 响应体文本)，用于断言失败分支。"""
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
    p = subprocess.run([MYSQL, "-u", DBUSER, "-p" + DBPASS, "-D", "supermarket_system",
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


def pending_request_id():
    out = sql(f"SELECT id FROM supermarket_system.password_reset_request "
              f"WHERE user_id={uid} AND status='PENDING' ORDER BY id DESC LIMIT 1")
    return int(out) if out else None


try:
    reg = call("POST", "/auth/register", {"username": user, "password": PW,
                                          "nickname": "找回验证", "phone": phone})[0]
    tok = reg["token"]
    uid = call("GET", "/auth/me", None, tok)[0]["id"]

    admin_tok = call("POST", "/auth/login", {"username": "admin", "password": "Admin123456"})[0]["token"]
    check("A0 管理员登录拿到 token", bool(admin_tok))

    # ---------- A. 提交申请 ----------
    data, _ = call("POST", "/auth/password-reset-request",
                   {"username": user, "contact": contact})
    msg_real = data.get("message") or ""
    check("A1 账号存在：提交成功且 submitted=true", data.get("submitted") is True, data)
    n = scalar(f"SELECT COUNT(*) FROM supermarket_system.password_reset_request "
               f"WHERE user_id={uid} AND status='PENDING'")
    check("A2 账号存在：落库 1 条 PENDING", n == "1", n)

    rid = pending_request_id()
    _, _ = call("POST", "/auth/password-reset-request", {"username": user, "contact": contact})
    n = scalar(f"SELECT COUNT(*) FROM supermarket_system.password_reset_request WHERE user_id={uid}")
    check("A3 同一账号重复提交幂等（仍只有 1 条）", n == "1", n)

    # ---------- B. 防账号枚举 ----------
    ghost, _ = call("POST", "/auth/password-reset-request",
                    {"username": ghost_user, "contact": contact})
    check("B1 不存在的账号也返回 submitted=true", ghost.get("submitted") is True, ghost)
    check("B2 两种情况的文案完全一致（防账号枚举）",
          (ghost.get("message") or "") == msg_real, repr(ghost.get("message")))
    n = scalar(f"SELECT COUNT(*) FROM supermarket_system.password_reset_request "
               f"WHERE username='{ghost_user}'")
    check("B3 不存在的账号不落库", n == "0", n)

    # ---------- C. 参数校验 ----------
    st, _ = call_status("POST", "/auth/password-reset-request", {"username": "", "contact": contact})
    check("C1 空账号被拒(400)", st == 400, st)
    st, _ = call_status("POST", "/auth/password-reset-request", {"username": user, "contact": ""})
    check("C2 空联系电话被拒(400)", st == 400, st)

    # ---------- D. 权限 ----------
    st, _ = call_status("GET", "/admin/password-reset-requests")
    check("D1 未登录访问后台申请列表被拒(401)", st == 401, st)
    st, _ = call_status("GET", "/admin/password-reset-requests", None, tok)
    check("D2 普通用户访问后台申请列表被拒(403)", st == 403, st)

    # ---------- E. 后台列表 ----------
    lst, _ = call("GET", "/admin/password-reset-requests?page=1&size=50&status=PENDING", None, admin_tok)
    ids = [item.get("id") for item in (lst or {}).get("items", [])]
    check("E1 待处理列表含刚提交的申请", rid in ids, f"rid={rid}")
    hit = next((i for i in (lst or {}).get("items", []) if i.get("id") == rid), {})
    check("E2 列表带出昵称与账号预留手机号", hit.get("nickname") == "找回验证" and hit.get("phone") == phone,
          {k: hit.get(k) for k in ("nickname", "phone", "contact")})
    check("E3 列表带出申请人填写的联系方式", hit.get("contact") == contact, hit.get("contact"))
    cnt, _ = call("GET", "/admin/password-reset-requests/pending-count", None, admin_tok)
    check("E4 待处理计数为数字且 ≥1", isinstance(cnt, int) and cnt >= 1, cnt)

    # ---------- F. 管理员重置 ----------
    res, _ = call("POST", f"/admin/password-reset-requests/{rid}/reset", {}, admin_tok)
    temp_pw = (res or {}).get("tempPassword") or ""
    check("F1 重置返回临时密码", bool(temp_pw), temp_pw)
    check("F2 临时密码为 10 位", len(temp_pw) == 10, len(temp_pw))
    check("F3 临时密码不等于原密码", temp_pw != PW)
    check("F4 响应带账号与手机号（供客服联系）",
          res.get("username") == user and res.get("phone") == phone,
          {k: res.get(k) for k in ("username", "phone")})
    stored = scalar(f"SELECT password_hash FROM supermarket_system.sys_user WHERE id={uid}")
    check("F5 库里只存哈希，不含临时密码明文", temp_pw not in stored and stored.startswith("$2"), stored[:12])
    n = scalar(f"SELECT COUNT(*) FROM supermarket_system.password_reset_request "
               f"WHERE id={rid} AND status='DONE'")
    check("F6 申请标记为 DONE", n == "1", n)

    # ---------- I. 不能重复重置 ----------
    st, _ = call_status("POST", f"/admin/password-reset-requests/{rid}/reset", {}, admin_tok)
    check("I1 已处理的申请不能重复重置(400)", st == 400, st)

    # ---------- G. 旧密码失效 / 临时密码可登录 / 强制改密标记 ----------
    st, _ = call_status("POST", "/auth/login", {"username": user, "password": PW})
    check("G1 旧密码已失效(401)", st == 401, st)
    login, _ = call("POST", "/auth/login", {"username": user, "password": temp_pw})
    check("G2 临时密码可登录", bool(login.get("token")))
    check("G3 登录响应带 mustChangePassword=true",
          login.get("user", {}).get("mustChangePassword") is True, login.get("user"))
    flag = scalar(f"SELECT must_change_password FROM supermarket_system.sys_user WHERE id={uid}")
    check("G4 库里标记为 1", flag == "1", flag)

    # ---------- H. 用临时密码改密 ----------
    tok2 = login["token"]
    st, _ = call_status("POST", "/auth/change-password",
                        {"currentPassword": temp_pw, "newPassword": NEW, "confirmPassword": NEW}, tok2)
    check("H1 用临时密码改密成功(200)", st == 200, st)
    flag = scalar(f"SELECT must_change_password FROM supermarket_system.sys_user WHERE id={uid}")
    check("H2 改密后强制改密标记被清除", flag == "0", flag)
    me, _ = call("GET", "/auth/me", None, tok2)
    check("H3 /auth/me 返回 mustChangePassword=false", me.get("mustChangePassword") is False, me.get("mustChangePassword"))
    st, _ = call_status("POST", "/auth/login", {"username": user, "password": NEW})
    check("H4 新密码可登录(200)", st == 200, st)
    st, _ = call_status("POST", "/auth/login", {"username": user, "password": temp_pw})
    check("H5 临时密码已失效(401)", st == 401, st)

    # ---------- J. 驳回流程 + 驳回后可重新申请 ----------
    _, _ = call("POST", "/auth/password-reset-request", {"username": user, "contact": contact})
    rid2 = pending_request_id()
    check("J1 处理完可再次提交新申请", rid2 is not None and rid2 != rid, rid2)
    rej, _ = call("POST", f"/admin/password-reset-requests/{rid2}/reject", {"remark": "身份核对未通过"}, admin_tok)
    check("J2 驳回成功且状态为 REJECTED", rej.get("status") == "REJECTED", rej.get("status"))
    st, _ = call_status("POST", f"/admin/password-reset-requests/{rid2}/reject", {"remark": "x"}, admin_tok)
    check("J3 已驳回的申请不能重复驳回(400)", st == 400, st)
    _, _ = call("POST", "/auth/password-reset-request", {"username": user, "contact": contact})
    check("J4 驳回后可重新提交（生成新的 PENDING）", pending_request_id() is not None)

except Exception as exc:  # noqa: BLE001
    check("EXCEPTION " + repr(exc), False)

finally:
    try:
        sql(f"DELETE FROM supermarket_system.password_reset_request WHERE username IN ('{user}', '{ghost_user}')")
        if uid is not None:
            for t in ("user_coupon", "user_message", "cart_item", "user_address", "point_ledger",
                      "user_favorite", "price_alert", "wallet_transaction"):
                sql(f"DELETE FROM supermarket_system.{t} WHERE user_id={uid}")
            sql(f"DELETE FROM supermarket_system.sys_user WHERE id={uid}")
            left = scalar(f"SELECT COUNT(*) FROM supermarket_system.password_reset_request WHERE user_id={uid}")
            check("K1 清理后无残留申请", left == "0", left)
            left_u = scalar(f"SELECT COUNT(*) FROM supermarket_system.sys_user WHERE id={uid}")
            check("K2 清理后无残留账号", left_u == "0", left_u)
        print("CLEANUP_OK")
    except Exception as exc:  # noqa: BLE001
        print("CLEANUP_FAIL:", exc)

passed = sum(1 for _, ok in results if ok)
print(f"\n==== {passed}/{len(results)} 项通过 ====")
for name, ok in results:
    if not ok:
        print("  FAILED:", name)
