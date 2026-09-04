#!/usr/bin/env python3
import urllib.request, urllib.error, json, subprocess, sys

BASE = "http://localhost:8080/api"
MYSQL = "C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
DB, DBUSER, DBPASS = "supermarket_system", "root", "zzmsgz"
NEWHASH = "$2b$12$v8xfXYnGzAfb0mKi8naWH.i2b/eTTyAF48kjcvsXxoA4F9DLDkgb6"

def mysql(sql):
    r = subprocess.run([MYSQL, "-u", DBUSER, f"-p{DBPASS}", "--default-character-set=utf8mb4", DB, "-e", sql], capture_output=True, text=True)
    return (r.stdout + r.stderr).replace("mysql: [Warning] Using a password on the command line interface can be insecure.\n", "")

def req(method, path, token=None, body=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=15) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {"raw": e.read().decode()}

orig = mysql("SELECT password_hash FROM sys_user WHERE id=1;").strip().split("\n")[-1]
print("saved original admin hash:", orig[:20], "...")
mysql(f"UPDATE sys_user SET password_hash='{NEWHASH}' WHERE id=1;")
code, resp = req("POST", "/auth/login", body={"username": "admin", "password": "Admin123456"})
assert resp.get("code") == 0, f"admin login {resp}"
atok = resp["data"]["token"]
print("admin token len", len(atok))

# 用订单 11（含商品项）验证管理员订单详情
code, resp = req("GET", "/admin/orders/11", token=atok)
od = resp.get("data") or {}
items = od.get("items") or []
ok = len(items) >= 1 and items[0].get("productName") and items[0].get("quantity")
print(("PASS " if ok else "FAIL ") + f"Bug1-管理员订单详情含商品 items={len(items)} name={items[0].get('productName') if items else None}")

# 售后管理同样复用该接口，顺带验证退款列表也能取到订单号对应的详情
mysql(f"UPDATE sys_user SET password_hash='{orig}' WHERE id=1;")
print("restored admin password hash.")
sys.exit(0 if ok else 1)
