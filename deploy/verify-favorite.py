"""收藏 + 降价提醒 端到端验证（后端 API 层）。

覆盖：
  A. 收藏：未登录 401、收藏写入基线价、重复收藏不覆盖基线、ids/列表口径
  B. 降价提醒：降价生成提醒（基线/现价/降幅/降幅百分比）、未读数、全部已读、
     再次下探重新变未读
  C. 自愈：价格回升到基线之上 → 提醒自动消失
  D. 取消收藏：连带清提醒、幂等；收藏不存在的商品 → 404

自建测试账号，finally 中还原商品价格并清库（删除收藏/提醒/测试用户）。
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

buyer = "favv_" + STAMP
PW = "Favv12345"
phone = "136" + STAMP[-8:]

uid = None
pid = None
orig_price = None


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
        raise RuntimeError(f"{method} {path} -> {status} {payload}")
    return payload.get("data")


def status_of(method, path, body=None, token=None):
    return request(method, path, body, token)[0]


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


def near(a, b, tol=0.011):
    return abs(float(a) - float(b)) <= tol


try:
    tok = call("POST", "/auth/register", {"username": buyer, "password": PW,
                                          "nickname": "收藏验证", "phone": phone})["token"]
    me = call("GET", "/auth/me", None, tok)
    uid = me["id"]

    # ---------- A. 收藏 ----------
    check("A1 未登录访问 /favorites 返回 401", status_of("GET", "/favorites") == 401)

    prods = call("GET", "/products?page=1&size=20", None, tok)["items"]
    prod = next(p for p in prods if p["stock"] > 5)
    pid = prod["id"]
    base = round(float(prod["price"]), 2)
    orig_price = base

    fav = call("POST", f"/favorites/{pid}", None, tok)
    check("A2 收藏写入基线价（=当前售价）", near(fav["priceAtFavorite"], base),
          (fav["priceAtFavorite"], base))
    check("A3 初始未降价", fav["priceDropped"] is False)

    # 降价后再重复收藏：基线不应被覆盖（否则降价提醒会被"洗掉"）
    price1 = round(base * 0.9, 2)
    sql(f"UPDATE supermarket_system.product SET price={price1} WHERE id={pid}")
    fav2 = call("POST", f"/favorites/{pid}", None, tok)
    check("A4 重复收藏幂等、基线不被覆盖", near(fav2["priceAtFavorite"], base),
          fav2["priceAtFavorite"])

    ids = call("GET", "/favorites/ids", None, tok)
    check("A5 /favorites/ids 含已收藏商品", pid in [int(x) for x in ids], ids)

    page = call("GET", "/favorites?page=1&size=12", None, tok)
    item = next((x for x in page["items"] if x["productId"] == pid), None)
    check("A6 收藏列表返回该商品", item is not None)
    if item:
        check("A7 列表现价=降价后的价格", near(item["currentPrice"], price1), item["currentPrice"])
        check("A8 列表标记已降价且降幅正确",
              item["priceDropped"] is True and near(item["dropAmount"], base - price1),
              (item["priceDropped"], item["dropAmount"]))
        check("A9 列表带商品摘要（含售价）",
              item["product"] is not None and near(item["product"]["price"], price1))

    # ---------- B. 降价提醒 ----------
    alerts = call("GET", "/price-alerts?page=1&size=12", None, tok)
    alert = next((a for a in alerts["items"] if a["productId"] == pid), None)
    check("B1 降价生成提醒", alert is not None and alerts["total"] >= 1)
    if alert:
        check("B2 提醒基线价=收藏时价格", near(alert["oldPrice"], base), alert["oldPrice"])
        check("B3 提醒现价=当前售价", near(alert["newPrice"], price1), alert["newPrice"])
        check("B4 降幅金额正确", near(alert["dropAmount"], round(base - price1, 2)),
              alert["dropAmount"])
        check("B5 降幅百分比正确(10%)", int(alert["dropPercent"]) == 10, alert["dropPercent"])
        check("B6 新提醒默认未读", alert["isRead"] is False)

    unread = call("GET", "/price-alerts/unread-count", None, tok)
    check("B7 未读数为 1", int(unread["count"]) == 1, unread["count"])

    marked = call("POST", "/price-alerts/read", None, tok)
    check("B8 全部已读返回更新条数", int(marked["updated"]) == 1, marked["updated"])
    check("B9 已读后未读数为 0",
          int(call("GET", "/price-alerts/unread-count", None, tok)["count"]) == 0)
    after = call("GET", "/price-alerts?page=1&size=12", None, tok)
    a2 = next((a for a in after["items"] if a["productId"] == pid), None)
    check("B10 提醒已标记为已读", a2 is not None and a2["isRead"] is True)

    # 再次下探 → 提醒刷新并重新变未读
    price2 = round(base * 0.8, 2)
    sql(f"UPDATE supermarket_system.product SET price={price2} WHERE id={pid}")
    again = call("GET", "/price-alerts?page=1&size=12", None, tok)
    a3 = next((a for a in again["items"] if a["productId"] == pid), None)
    check("B11 再次降价刷新现价", a3 is not None and near(a3["newPrice"], price2),
          a3 and a3["newPrice"])
    check("B12 再次降价重新变未读", a3 is not None and a3["isRead"] is False)
    check("B13 未读数回到 1",
          int(call("GET", "/price-alerts/unread-count", None, tok)["count"]) == 1)
    check("B14 同一商品只有一条提醒",
          len([a for a in again["items"] if a["productId"] == pid]) == 1)

    # ---------- C. 自愈：价格回升 ----------
    sql(f"UPDATE supermarket_system.product SET price={base} WHERE id={pid}")
    healed = call("GET", "/price-alerts?page=1&size=12", None, tok)
    check("C1 价格回升后提醒自动消失",
          all(a["productId"] != pid for a in healed["items"]) and healed["total"] == 0,
          healed["total"])
    check("C2 未读数归零",
          int(call("GET", "/price-alerts/unread-count", None, tok)["count"]) == 0)

    # ---------- D. 取消收藏 / 异常 ----------
    removed = call("DELETE", f"/favorites/{pid}", None, tok)
    check("D1 取消收藏成功", removed["removed"] is True)
    check("D2 ids 不再包含该商品",
          pid not in [int(x) for x in call("GET", "/favorites/ids", None, tok)])
    check("D3 收藏列表为空",
          call("GET", "/favorites?page=1&size=12", None, tok)["total"] == 0)
    removed2 = call("DELETE", f"/favorites/{pid}", None, tok)
    check("D4 重复取消收藏幂等", removed2["removed"] is False)

    check("D5 收藏不存在的商品返回 404", status_of("POST", "/favorites/99999999", None, tok) == 404)

    # 重新收藏时基线应重置为「当时的售价」
    price3 = round(base * 0.7, 2)
    sql(f"UPDATE supermarket_system.product SET price={price3} WHERE id={pid}")
    refav = call("POST", f"/favorites/{pid}", None, tok)
    check("D6 重新收藏后基线重置为当时售价", near(refav["priceAtFavorite"], price3),
          (refav["priceAtFavorite"], price3))
    check("D7 基线之上无提醒（未降价）", refav["priceDropped"] is False)

except Exception as exc:  # noqa: BLE001
    check("EXCEPTION " + repr(exc), False)

finally:
    try:
        if pid is not None and orig_price is not None:
            sql(f"UPDATE supermarket_system.product SET price={orig_price} WHERE id={pid}")
        if uid is not None:
            for t in ("price_alert", "user_favorite", "user_coupon", "cart_item",
                      "user_address", "point_ledger", "wallet_transaction"):
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
