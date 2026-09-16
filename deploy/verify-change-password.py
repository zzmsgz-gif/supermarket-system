"""修改密码功能端到端验证（后端 API 层）。

覆盖：
  A. 正确原密码可改，改后新密码可登录、旧密码失效
  B. 错误原密码被拒（400）
  C. 两次新密码不一致被拒（400）
  D. 新密码与原密码相同被拒（400）
  E. 未登录调用被拒（401）

自建单一测试账号，finally 中按依赖顺序清理（user_coupon / user_message / sys_user）。
"""
import json
import subprocess
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
DBPASS = "zzmsgz"
STAMP = str(int(time.time()))
results = []

user = "cpw_" + STAMP
PW = "OldPass123"
NEW = "NewPass456"
phone = "139" + STAMP[-8:]
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
    """返回 (status_code, message)，用于断言失败分支。"""
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Content-Type": "application/json",
                 **({"Authorization": "Bearer " + token} if token else {})},
        method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, ""
    except urllib.error.HTTPError as err:
        return err.code, err.read().decode()


def sql(stmt):
    p = subprocess.run([MYSQL, "-uroot", "-p" + DBPASS, "-D", "supermarket_system",
                        "--default-character-set=utf8mb4", "-N", "-B", "-e", stmt],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if p.returncode != 0:
        raise RuntimeError("SQL fail: " + p.stderr)
    return p.stdout.strip()


def check(name, cond, detail=""):
    results.append((name, bool(cond)))
    print(("PASS  " if cond else "FAIL  ") + name + (("  | " + str(detail)) if detail else ""))


try:
    reg = call("POST", "/auth/register", {"username": user, "password": PW,
                                          "nickname": "改密验证", "phone": phone})[0]
    tok = reg["token"]
    uid = call("GET", "/auth/me", None, tok)[0]["id"]

    # A. 正确原密码改密 + 新旧密码切换
    st, _ = call_status("POST", "/auth/change-password",
                        {"currentPassword": PW, "newPassword": NEW, "confirmPassword": NEW}, tok)
    check("A1 正确原密码改密成功(200)", st == 200, st)
    st, _ = call_status("POST", "/auth/login", {"username": user, "password": NEW})
    check("A2 新密码可登录(200)", st == 200, st)
    st, _ = call_status("POST", "/auth/login", {"username": user, "password": PW})
    check("A3 旧密码已失效(401)", st == 401, st)

    # B. 错误原密码
    st, _ = call_status("POST", "/auth/change-password",
                        {"currentPassword": "WrongOld1", "newPassword": "Xyz987654", "confirmPassword": "Xyz987654"},
                        tok)
    check("B1 错误原密码被拒(400)", st == 400, st)

    # C. 两次新密码不一致
    st, _ = call_status("POST", "/auth/change-password",
                        {"currentPassword": NEW, "newPassword": "Xyz987654", "confirmPassword": "Xyz000000"}, tok)
    check("C1 两次新密码不一致被拒(400)", st == 400, st)

    # D. 新密码与原密码相同
    st, _ = call_status("POST", "/auth/change-password",
                        {"currentPassword": NEW, "newPassword": NEW, "confirmPassword": NEW}, tok)
    check("D1 新密码与原密码相同被拒(400)", st == 400, st)

    # E. 未登录
    st, _ = call_status("POST", "/auth/change-password",
                        {"currentPassword": NEW, "newPassword": "Abc123456", "confirmPassword": "Abc123456"})
    check("E1 未登录调用被拒(401)", st == 401, st)

    # 还原：把密码改回原值，便于若需复跑；清理时仍会删用户
    try:
        tok2 = call("POST", "/auth/login", {"username": user, "password": NEW})[0]["token"]
        call("POST", "/auth/change-password",
             {"currentPassword": NEW, "newPassword": PW, "confirmPassword": PW}, tok2)
    except Exception:
        pass

except Exception as exc:  # noqa: BLE001
    check("EXCEPTION " + repr(exc), False)

finally:
    try:
        if uid is not None:
            for t in ("user_coupon", "user_message", "cart_item", "user_address"):
                sql(f"DELETE FROM supermarket_system.{t} WHERE user_id={uid}")
            sql(f"DELETE FROM supermarket_system.sys_user WHERE id={uid}")
        print("CLEANUP_OK")
    except Exception as exc:  # noqa: BLE001
        print("CLEANUP_FAIL:", exc)

passed = sum(1 for _, ok in results if ok)
print(f"\n==== {passed}/{len(results)} 项通过 ====")
for name, ok in results:
    if not ok:
        print("  FAILED:", name)
