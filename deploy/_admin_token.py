"""给验证脚本签发一个**临时管理员**登录态 —— 绝不改 `sys_user id=1` 的密码。

背景（PROGRESS 未决项①）：`verify-admin-order.py` / `verify-admin-stock-edit.py` 早期会把
admin(id=1) 的密码临时改成硬编码值、跑完再改回。问题是**中途崩溃就不会改回** ——
限流 429 中断、被超时强杀、assert 失败、Ctrl-C 都会把 admin 密码留在那个硬编码值上，
而本仓库是**公开仓库**，等于把后台口令公开出去。

这里改用「注册临时买家 → 提权为 ADMIN → 登录」，用完即删，`id=1` 全程不碰。

用法：

    from _admin_token import temp_admin

    with temp_admin() as adm:
        adm.token      # 带 ROLE_ADMIN 的 JWT
        adm.uid        # 临时账号 id（一般不用管）
        adm.username   # 形如 vtmpadm_1789...
        adm.sql("SELECT ...")   # 复用同一个连接参数（见 _db）

清理顺序照 `CLEANUP.md`：先断开 `stock_log.operator_id`（该列可空，比删流水轻），
再删用户级流水，最后删账号。同时注册了「按用户名兜底」的 atexit，防止在拿到 uid 之前
就中断（这正是 `p0-smoke-test.py` 漏账号的成因）。
"""
import atexit
import json
import subprocess
import time
import urllib.error
import urllib.request

from _db import DB, DBPASS, DBUSER, MYSQL_BIN

BASE = "http://localhost:8080/api"
PASSWORD = "TmpAdmin12345"
USER_PREFIX = "vtmpadm"

# 注册产生的用户级流水（删账号前必须清，否则外键挡住）
_USER_ROWS = (
    "product_review", "wallet_transaction", "user_message", "point_ledger",
    "user_favorite", "price_alert", "cart_item", "user_address", "user_coupon",
)


def _sql(statement):
    p = subprocess.run(
        [MYSQL_BIN, "-u", DBUSER, "-p" + DBPASS, "--default-character-set=utf8mb4",
         DB, "-N", "-B", "-e", statement],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    if p.returncode != 0:
        raise RuntimeError("SQL failed: " + (p.stderr or "")[:300])
    return p.stdout.strip()


def _req(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method,
                               headers={"Content-Type": "application/json"})
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=15) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}


class TempAdmin:
    def __init__(self, username, uid, token):
        self.username, self.uid, self.token = username, uid, token
        self._cleaned = False

    def sql(self, statement):
        return _sql(statement)

    def cleanup(self):
        """删掉临时账号（幂等）。"""
        if self._cleaned:
            return
        self._cleaned = True
        try:
            _sql(f"UPDATE stock_log SET operator_id=NULL WHERE operator_id={self.uid}")
            for t in _USER_ROWS:
                _sql(f"DELETE FROM {t} WHERE user_id={self.uid}")
            _sql(f"DELETE FROM orders WHERE user_id={self.uid}")
            _sql(f"DELETE FROM sys_user WHERE id={self.uid} AND username LIKE '{USER_PREFIX}\\_%'")
        except Exception as e:                                     # 清理失败不该掩盖验证结果
            print(f"[temp_admin] ⚠️ 清理失败，请手工核查 sys_user WHERE username LIKE '{USER_PREFIX}_%'：{e}")

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        self.cleanup()
        return False


def _fallback_cleanup_by_name(username):
    """拿到 uid 之前就中断时的兜底（按用户名删，幂等）。"""
    try:
        _sql(f"UPDATE stock_log SET operator_id=NULL WHERE operator_id IN "
             f"(SELECT id FROM (SELECT id FROM sys_user WHERE username='{username}') x)")
        _sql(f"DELETE FROM sys_user WHERE username='{username}'")
    except Exception:
        pass


def temp_admin(nickname="临时管理员"):
    """创建并返回一个 `TempAdmin`（记得用 `with`，或自行调用 `.cleanup()`）。"""
    ts = int(time.time())
    username = f"{USER_PREFIX}_{ts}"
    phone = "13" + str(ts)[-9:].zfill(9)          # 11 位、第二位是 3，能过手机号校验

    atexit.register(_fallback_cleanup_by_name, username)   # 先挂兜底，再动手创建

    st, body = _req("POST", "/auth/register", {"username": username, "password": PASSWORD,
                                               "phone": phone, "nickname": nickname})
    if st != 200 or body.get("code") != 0:
        raise SystemExit(f"注册临时账号失败：HTTP {st} {body}")

    uid = int(_sql(f"SELECT id FROM sys_user WHERE username='{username}'").split()[0])
    _sql(f"UPDATE sys_user SET role='ADMIN' WHERE id={uid}")

    st, body = _req("POST", "/auth/login", {"username": username, "password": PASSWORD})
    if body.get("code") != 0:
        raise SystemExit(f"临时管理员登录失败：HTTP {st} {body}")

    adm = TempAdmin(username, uid, body["data"]["token"])
    atexit.register(adm.cleanup)      # 正常结束 / sys.exit / 未捕获异常都会清；cleanup 幂等
    return adm
