"""验证 / 清理脚本共用的数据库连接参数。

⚠️ 本仓库是**公开仓库**：任何脚本都不要再写死数据库口令（历史上 28 个 `deploy/*.py`
都硬编码了本机 MySQL 口令，等于把口令公开出去了）。

解析顺序（前面的优先）：
  1. 环境变量 `SM_DB_HOST` / `SM_DB_PORT` / `SM_DB_NAME` / `SM_DB_USER` / `SM_DB_PASS`（`MYSQL_PWD` 兜底）
  2. `backend/src/main/resources/application.yml` 的 `spring.datasource.{url,username,password}`
     （该文件**已在 .gitignore 里**，是"本地密钥不提交"的正解；支持 `${ENV:default}` 占位符）
  3. 都取不到 → **直接报错退出**（绝不回落到硬编码值）

用法（脚本照旧写在 `deploy/` 下、用 `python deploy/xxx.py` 运行即可）：

    from _db import DB, DBUSER, DBPASS                   # 旧脚本沿用这三个名字
    from _db import HOST, PORT, USER, PASS, DB           # seed_catalog 那套名字

    subprocess.run([MYSQL, "-u", DBUSER, "-p" + DBPASS, DB, "-N", "-e", sql])

也可以直接用便捷函数：

    from _db import run_sql                              # 返回 stdout+stderr 文本，已滤掉命令行口令告警
    from _db import mysql_args                           # 拼命令行参数，便于自己控制 subprocess
"""
import os
import pathlib
import re
import subprocess

_YML = pathlib.Path(__file__).resolve().parent.parent / "backend/src/main/resources/application.yml"
_DEFAULT_MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"

# mysql 每次都会把这条告警打到 stderr，统一滤掉，脚本里的字符串比对才干净
_WARN = "mysql: [Warning] Using a password on the command line interface can be insecure."


def _resolve_placeholder(value):
    """把 `${ENV:default}` 解成实际值（默认值里可能含冒号，所以只按第一个冒号切）。"""
    value = (value or "").strip()
    m = re.fullmatch(r"\$\{([^:}]+)(?::(.*))?\}", value, re.S)
    if not m:
        return value
    env_name, default = m.group(1), m.group(2)
    return os.environ.get(env_name, default if default is not None else "")


def _from_yml(key):
    try:
        txt = _YML.read_text(encoding="utf-8")
    except OSError:
        return ""
    i = txt.find("datasource:")
    if i < 0:
        return ""
    m = re.search(r"^\s*" + key + r":\s*(.+?)\s*$", txt[i:], re.M)
    return _resolve_placeholder(m.group(1)) if m else ""


def _parse_jdbc(url):
    m = re.search(r"//([^:/?]+)(?::(\d+))?/([^?]+)", url or "")
    if not m:
        return "", None, ""
    return m.group(1), (int(m.group(2)) if m.group(2) else None), m.group(3)


_host, _port, _name = _parse_jdbc(_from_yml("url"))

HOST = os.environ.get("SM_DB_HOST") or _host or "127.0.0.1"
PORT = int(os.environ.get("SM_DB_PORT") or _port or 3306)
USER = os.environ.get("SM_DB_USER") or _from_yml("username") or "root"
DB = os.environ.get("SM_DB_NAME") or _name or "supermarket_system"
PASS = (
    os.environ.get("SM_DB_PASS")
    or _from_yml("password")
    or os.environ.get("MYSQL_PWD")
    or ""
)

if not PASS:
    raise SystemExit(
        "找不到数据库口令。请任选一种方式：\n"
        "  1. 设置环境变量 SM_DB_PASS=... （临时运行推荐）\n"
        f"  2. 确认 {_YML} 里 spring.datasource.password 有值\n"
        "（刻意**不**提供硬编码回落值 —— 本仓库是公开仓库）"
    )

# 旧脚本沿用的别名
DBPASS = PASS
DBUSER = USER
DB_PASS = PASS
DB_NAME = DB
MYSQL_BIN = os.environ.get("SM_MYSQL_BIN") or _DEFAULT_MYSQL


def mysql_args(*extra, host=False):
    """拼出 mysql 命令行参数（含 -h/-P/-u/-p），`extra` 追加在末尾。"""
    args = [MYSQL_BIN]
    if host:
        args += ["-h", HOST, "-P", str(PORT)]
    args += ["-u", USER, "-p" + PASS]
    return args + list(extra)


def run_sql(sql, database=DB, extra=("--default-character-set=utf8mb4",)):
    """执行一段 SQL，返回 (stdout+stderr 文本)。默认带上 utf8mb4（带中文的 SQL 不加会 ERROR 1366）。"""
    args = mysql_args(*extra)
    if database:
        args += [database]
    args += ["-e", sql]
    r = subprocess.run(args, capture_output=True, text=True, encoding="utf-8", errors="replace")
    return (r.stdout + r.stderr).replace(_WARN + "\n", "").replace(_WARN, "")
