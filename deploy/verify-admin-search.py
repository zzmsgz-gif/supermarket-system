"""Admin 搜索 + 分页 契约校验（读操作，不改动业务数据）。"""
import json
import urllib.request

BASE = "http://localhost:8080/api"


def req(method, path, token=None, data=None):
    url = BASE + path
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    body = json.dumps(data).encode() if data is not None else None
    r = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=15) as resp:
            return resp.status, json.loads(resp.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode() or "{}")


def login(username, password):
    s, d = req("POST", "/auth/login", data={"username": username, "password": password})
    assert s == 200 and d.get("code") == 0, f"login failed: {s} {d}"
    return d["data"]["token"]


def check(name, cond, extra=""):
    print(f"[{'PASS' if cond else 'FAIL'}] {name} {extra}")


def main():
    token = login("admin", "123456")

    # ---- 优惠券：关键词搜索（新增后端能力）----
    s, d = req("GET", "/admin/coupons?page=1&size=100", token)
    total_all = d.get("data", {}).get("total", 0)
    items_all = d.get("data", {}).get("items", [])
    check("优惠券 列表分页结构", s == 200 and "total" in d.get("data", {}) and "items" in d.get("data", {}), f"total={total_all}")

    if items_all:
        kw = items_all[0]["name"][:2]
        s2, d2 = req("GET", f"/admin/coupons?keyword={urllib.parse.quote(kw)}&page=1&size=10", token)
        items_kw = d2.get("data", {}).get("items", [])
        ok = all(kw.lower() in (c["name"] or "").lower() for c in items_kw)
        check("优惠券 关键词搜索生效", s2 == 200 and ok, f"kw='{kw}' 命中 {len(items_kw)} 张")
    else:
        check("优惠券 关键词搜索", True, "无券可测，跳过")

    # ---- 用户：关键词 + 角色 + 状态 ----
    s, d = req("GET", "/admin/users?page=1&size=5&keyword=admin", token)
    check("用户 关键词+分页", s == 200 and "total" in d.get("data", {}), f"total={d.get('data',{}).get('total')}")
    s, d = req("GET", "/admin/users?page=1&size=5&role=ADMIN", token)
    admins = d.get("data", {}).get("items", [])
    check("用户 角色筛选", s == 200 and all(u.get("role") == "ADMIN" for u in admins), f"admin数={len(admins)}")

    # ---- 订单：状态 + 订单号 + 分页 ----
    s, d = req("GET", "/admin/orders?page=1&size=5&status=PAID", token)
    orders = d.get("data", {}).get("items", [])
    check("订单 状态筛选+分页", s == 200 and "total" in d.get("data", {}), f"total={d.get('data',{}).get('total')} PAID数={len(orders)}")
    if orders:
        no = orders[0]["orderNo"]
        s2, d2 = req("GET", f"/admin/orders?page=1&size=5&orderNo={urllib.parse.quote(no)}", token)
        check("订单 订单号搜索", s2 == 200 and d2.get("data", {}).get("total", 0) >= 1, f"orderNo='{no}'")

    # ---- 售后：退款状态筛选 ----
    s, d = req("GET", "/admin/orders?refundStatus=APPLYING&page=1&size=10", token)
    refunds = d.get("data", {}).get("items", [])
    check("售后 退款状态筛选", s == 200 and all(o.get("refundStatus") == "APPLYING" for o in refunds), f"APPLYING数={len(refunds)}")

    print("DONE")


if __name__ == "__main__":
    import urllib.parse
    main()
