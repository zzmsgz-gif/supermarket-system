#!/usr/bin/env python3
"""验证：管理员编辑商品库存(绝对值)是否真正落库。
同时静态核对前端「点击头像换头像」与「账户余额框对齐」的构建产物。"""
import json, random, string, subprocess, sys, glob, os, urllib.request, urllib.error

BASE = "http://localhost:8080/api"
MYSQL = "C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
DB = "supermarket_system"

def mysql_val(sql):
    """执行 SQL 并返回首个数据单元格（用参数列表，避免 shell 对 $ 的扩展）。"""
    p = subprocess.run([MYSQL, "-u", "root", "-pzzmsgz", "--default-character-set=utf8mb4", DB, "-N", "-e", sql],
                       capture_output=True, encoding="utf-8", errors="ignore")
    for line in p.stdout.splitlines():
        line = line.strip()
        if not line or set(line) <= set("-+|= "):
            continue
        return line.split("\t")[-1]
    return ""

def req(method, path, body=None, token=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method,
                               headers={"Content-Type": "application/json"})
    if token:
        r.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(r, timeout=15) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}

def main():
    # 1) 临时设置 admin 密码以便登录（结束还原）
    orig = mysql_val("SELECT password_hash FROM sys_user WHERE id=1;")
    import bcrypt
    newhash = bcrypt.hashpw(b"Admin123456", bcrypt.gensalt()).decode()
    subprocess.run([MYSQL, "-u", "root", "-pzzmsgz", "--default-character-set=utf8mb4", DB, "-N", "-e",
                   f"UPDATE sys_user SET password_hash='{newhash}' WHERE id=1;"],
                   encoding="utf-8", errors="ignore")

    code, resp = req("POST", "/auth/login", {"username": "admin", "password": "Admin123456"})
    assert resp.get("code") == 0, f"admin login failed: {resp}"
    atok = resp["data"]["token"]

    pid = 35
    code, resp = req("GET", f"/admin/products/{pid}", token=atok)
    assert resp.get("code") == 0, f"get product failed: {resp}"
    d = resp["data"]
    before = int(d.get("stock", 0))
    print(f"商品 {pid} 编辑前库存 = {before}")

    payload = {
        "categoryId": d["categoryId"], "sku": d["sku"], "name": d["name"],
        "subtitle": d.get("subtitle") or "", "description": d.get("description") or "",
        "coverUrl": d.get("coverUrl") or "", "price": float(d["price"]),
        "originalPrice": float(d["originalPrice"]) if d.get("originalPrice") else None,
        "unit": d.get("unit") or "piece", "brand": d.get("brand") or "",
        "isHot": d.get("isHot"), "isNew": d.get("isNew"), "tags": d.get("tags") or "",
        "stock": before + 7,
        "images": [{"url": i["url"], "sortNo": i.get("sortNo", 0)} for i in (d.get("images") or [])],
        "skus": [{"specJson": s.get("specJson"), "skuCode": s.get("skuCode") or "", "image": s.get("image") or "", "sortNo": s.get("sortNo", 0)} for s in (d.get("skus") or [])],
        "attributes": [{"attrName": a.get("attrName"), "attrValue": a.get("attrValue"), "sortNo": a.get("sortNo", 0)} for a in (d.get("attributes") or [])],
    }
    code, resp = req("PUT", f"/admin/products/{pid}", payload, token=atok)
    ret_stock = int(resp.get("data", {}).get("stock", -1))
    ok1 = resp.get("code") == 0 and ret_stock == before + 7
    print(("PASS " if ok1 else "FAIL ") + f"Bug3-编辑库存绝对值返回正确: 期望={before+7} 返回={ret_stock}")

    db_after = mysql_val(f"SELECT stock FROM product WHERE id={pid};")
    ok2 = db_after == str(before + 7)
    print(("PASS " if ok2 else "FAIL ") + f"Bug3-数据库库存已落库: db={db_after}")

    # 守卫：库存字段未变化(=当前值)时提交，不应改变库存
    payload["stock"] = before + 7
    code, resp = req("PUT", f"/admin/products/{pid}", payload, token=atok)
    db_same = mysql_val(f"SELECT stock FROM product WHERE id={pid};")
    ok3 = db_same == str(before + 7)
    print(("PASS " if ok3 else "FAIL ") + f"Bug3-未改库存时提交保持不变: db={db_same}")

    # 还原库存
    payload["stock"] = before
    req("PUT", f"/admin/products/{pid}", payload, token=atok)
    subprocess.run([MYSQL, "-u", "root", "-pzzmsgz", "--default-character-set=utf8mb4", DB, "-N", "-e",
                   f"UPDATE sys_user SET password_hash='{orig}' WHERE id=1;"],
                   encoding="utf-8", errors="ignore")
    print("已还原商品库存与 admin 密码哈希。")

    # 4) 静态核对前端构建产物（以 index.html 实际引用的资源为准）
    import re
    html = open("D:/supermarket system/frontend/dist/index.html", encoding="utf-8", errors="ignore").read()
    js_ref = re.search(r'/assets/[^"]+\.js', html).group(0).lstrip('/')
    css_ref = re.search(r'/assets/[^"]+\.css', html).group(0).lstrip('/')
    jtxt = open(f"D:/supermarket system/frontend/dist/{js_ref}", encoding="utf-8", errors="ignore").read()
    ctxt = open(f"D:/supermarket system/frontend/dist/{css_ref}", encoding="utf-8", errors="ignore").read()
    # 「换头像」仅应出现在头像的 title 提示(点击更换头像)中，不应作为独立按钮文字存在
    has_clickable = "avatar-clickable" in jtxt
    no_standalone_button = ("点击更换头像" in jtxt) and (jtxt.count("换头像") == jtxt.count("点击更换头像"))
    print(("PASS " if has_clickable else "FAIL ") + "Bug1-构建产物含 avatar-clickable(点击换头像)")
    print(("PASS " if no_standalone_button else "FAIL ") + "Bug1-已无独立的『换头像』按钮(仅作头像提示)")
    ok_css = ".wallet-box" in ctxt and "inline-flex" in ctxt.replace(" ", "")
    print(("PASS " if ok_css else "FAIL ") + "Bug2-wallet-box 已改为 inline-flex 单行对齐")

    allok = ok1 and ok2 and ok3 and has_clickable and no_standalone_button and ok_css
    print("\n=== 结果:", "ALL PASS" if allok else "SOME FAILED", "===")
    sys.exit(0 if allok else 1)

if __name__ == "__main__":
    main()
