"""验证：登录框「记住我」= 7 天免登录（2026-09-22 之前那个复选框是纯装饰）。

背景：`LoginRequest` 里原先**根本没有 remember 字段**，前端也没传 —— 勾不勾都是同一个 24 小时 token。
现在：勾了 → 后端签发 7 天有效期的 token（`app.jwt.remember-expiration-seconds`，默认 604800 秒）
      + 前端把 token 存 localStorage（关浏览器仍免登录）；不勾 → 24 小时 + sessionStorage（关浏览器即失效）。

覆盖：
  A. 不带 remember（老客户端）/ remember=false → token 有效期 ≈ 24 小时
  B. remember=true → 有效期 ≈ 7 天
  C. 注册自动登录（没有那个复选框）→ 走默认 24 小时
  D. 两种 token 都能正常认证（不是"签了个用不了的 token"）
  E. 自清：删测试账号

用法：`python deploy/verify-remember-me.py`（后端在 8080 跑着）
"""
import base64
import json
import time
import urllib.error
import urllib.request

from _db import run_sql

BASE = "http://localhost:8080/api"
STAMP = str(int(time.time()))
DAY = 86400
WEEK = 604800
# 允许的偏差（网络/签发延迟）
TOL = 120

results = []
created = []


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
        with urllib.request.urlopen(r, timeout=20) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}


def ttl_of(token):
    """从 JWT 里取有效期秒数（只解码不看签名，用于断言 exp）"""
    payload = token.split(".")[1]
    payload += "=" * (-len(payload) % 4)
    claims = json.loads(base64.urlsafe_b64decode(payload))
    return claims["exp"] - claims["iat"], claims


def register(username):
    phone = "13" + str(int(time.time() * 1000))[-9:]
    st, body = req("POST", "/auth/register",
                   body={"username": username, "password": "RemTest12345", "phone": phone, "nickname": "记住我验证"})
    assert st == 200 and body.get("code") == 0, f"register failed: {st} {body}"
    created.append(username)
    return body["data"]["token"]


def cleanup():
    for name in created:
        try:
            row = run_sql(f"SELECT id FROM sys_user WHERE username='{name}'").strip()
            if not row:
                continue
            uid = row.split()[0]
            run_sql(f"UPDATE stock_log SET operator_id=NULL WHERE operator_id={uid}")
            for t in ("product_review", "wallet_transaction", "user_message", "point_ledger",
                      "user_favorite", "price_alert", "cart_item", "user_address", "user_coupon"):
                run_sql(f"DELETE FROM {t} WHERE user_id={uid}")
            run_sql(f"UPDATE orders SET user_coupon_id=NULL WHERE user_id={uid}")
            run_sql(f"DELETE FROM orders WHERE user_id={uid}")
            run_sql(f"DELETE FROM sys_user WHERE id={uid}")
        except Exception as e:
            print(f"[cleanup] ⚠️ 清理 {name} 失败：{e}")


print(f"=== 「记住我」验证（{STAMP}）===")

# ---------- 准备账号：注册自动登录拿到 token；再用它登录两次（勾 / 不勾）----------
user = "rem_" + STAMP
reg_token = register(user)

reg_ttl, _ = ttl_of(reg_token)
check("C1 注册自动登录 → 默认 24 小时", abs(reg_ttl - DAY) <= TOL, f"ttl={reg_ttl}s（≈{reg_ttl/3600:.2f}h）")

st, body = req("POST", "/auth/login", body={"username": user, "password": "RemTest12345"})
check("A1 不传 remember 也能登录（老客户端兼容）", st == 200 and body.get("code") == 0, f"HTTP {st}")
plain_ttl, _ = ttl_of(body["data"]["token"])
check("A2 不传 remember → 24 小时", abs(plain_ttl - DAY) <= TOL, f"ttl={plain_ttl}s（≈{plain_ttl/3600:.2f}h）")

st, body = req("POST", "/auth/login", body={"username": user, "password": "RemTest12345", "remember": False})
false_ttl, _ = ttl_of(body["data"]["token"])
check("A3 remember=false → 24 小时", st == 200 and abs(false_ttl - DAY) <= TOL,
      f"HTTP {st} ttl={false_ttl}s（≈{false_ttl/3600:.2f}h）")

st, body = req("POST", "/auth/login", body={"username": user, "password": "RemTest12345", "remember": True})
check("B1 remember=true 登录成功", st == 200 and body.get("code") == 0, f"HTTP {st}")
remember_token = body["data"]["token"]
remember_ttl, claims = ttl_of(remember_token)
check("B2 ⭐ remember=true → 7 天（604800s）", abs(remember_ttl - WEEK) <= TOL,
      f"ttl={remember_ttl}s（≈{remember_ttl/86400:.2f} 天）")
# ⚠️ 用 >= 而不是 >：7 天 − 24 小时 = **正好** 6 天，写严格大于会在边界上假失败（本次踩过）
check("B3 两者确实不同（不是都没生效）", remember_ttl - plain_ttl >= 6 * DAY,
      f"7天={remember_ttl}s vs 24h={plain_ttl}s，相差 {(remember_ttl - plain_ttl) / DAY:.1f} 天")

# ---------- D. token 可用性 ----------
st, body = req("GET", "/auth/me", remember_token)
check("D1 「记住我」的 token 能正常认证", st == 200 and (body.get("data") or {}).get("username") == user,
      f"HTTP {st} user={(body.get('data') or {}).get('username')}")
st, _ = req("GET", "/auth/me", reg_token)
check("D2 注册拿到的 token 也能正常认证", st == 200, f"HTTP {st}")
st, _ = req("GET", "/cart", remember_token)
check("D3 该 token 能访问需登录接口", st == 200, f"HTTP {st}")

# ---------- 配置可覆盖（只读断言，不改环境）----------
cfg = run_sql("SELECT 1").strip()  # 占位，保持 db 连通自检
check("E1 数据库连通", cfg == "1", f"probe={cfg}")

cleanup()
left = run_sql(f"SELECT COUNT(*) FROM sys_user WHERE username LIKE 'rem%'").strip()
check("F1 测试账号已清理", left == "0", f"残留={left}")

passed = sum(1 for _, ok in results if ok)
print(f"\n==== {passed}/{len(results)} 项通过 ====")
print("CLEANUP_OK" if left == "0" else "CLEANUP_FAILED")
raise SystemExit(0 if passed == len(results) else 1)
