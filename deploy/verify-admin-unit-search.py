"""验证后台：计价单位保存 + 查询/分页接口契约（后端此前已支持，本脚本仅做契约确认 + 清理）。"""
import json
import urllib.request
import urllib.error

BASE = "http://localhost:8080/api"


def req(method, path, body=None, token=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=10) as resp:
            text = resp.read().decode()
            return resp.status, (json.loads(text) if text else None)
    except urllib.error.HTTPError as e:
        text = e.read().decode()
        return e.code, (json.loads(text) if text else None)


def main():
    # 1) 管理员登录
    st, body = req("POST", "/auth/login", {"username": "admin", "password": "123456"})
    assert st == 200 and body and body.get("code") == 0, f"登录失败: {st} {body}"
    token = body["data"]["token"]
    print("[ok] 管理员登录成功")

    # 2) 列表分页 + 总数（验证后端 page/size/total 契约）
    st, body = req("GET", "/admin/products?page=1&size=3", token=token)
    assert st == 200 and body.get("code") == 0, f"列表失败: {st} {body}"
    data = body["data"]
    print(f"[ok] 商品列表: total={data['total']} page={data['page']} size={data['size']} 返回条数={len(data['items'])}")
    assert data["total"] >= 0 and len(data["items"]) <= data["size"]

    # 3) 关键字查询（按名称/编号）
    kw = ""
    if data["items"]:
        kw = data["items"][0]["name"][:2]
        st, body = req("GET", f"/admin/products?page=1&size=10&keyword={kw}", token=token)
        assert st == 200 and body.get("code") == 0
        print(f"[ok] 关键字「{kw}」查询: 命中 {body['data']['total']} 条")
        assert body["data"]["total"] >= 1

    # 4) 状态过滤
    st, body = req("GET", "/admin/products?page=1&size=5&status=ON_SALE", token=token)
    assert st == 200 and body.get("code") == 0
    print(f"[ok] 状态=ON_SALE 过滤: 命中 {body['data']['total']} 条")

    # 5) 新增一个「袋」计价单位的商品，验证 unit 落库
    payload = {
        "categoryId": 1,
        "sku": "VERIFY-UNIT-001",
        "name": "验证计价单位-袋装米",
        "price": 39.9,
        "stock": 50,
        "lowStockThreshold": 10,
        "unit": "bag",
        "status": "ON_SALE",
    }
    st, body = req("POST", "/admin/products", payload, token=token)
    assert st == 200 and body.get("code") == 0, f"新增失败: {st} {body}"
    new_id = body["data"]["id"]
    print(f"[ok] 新增商品(unit=bag)成功 id={new_id}")

    # 6) 取回确认 unit 字段
    st, body = req("GET", f"/admin/products/{new_id}", token=token)
    assert st == 200 and body.get("code") == 0
    assert body["data"]["unit"] == "bag", f"unit 未正确保存: {body['data'].get('unit')}"
    print(f"[ok] 回显确认 unit={body['data']['unit']}")

    # 7) 清理：删除该验证商品
    st, body = req("DELETE", f"/admin/products/{new_id}", token=token)
    assert st == 200 and body.get("code") == 0, f"删除失败: {st} {body}"
    print(f"[ok] 已清理验证商品 id={new_id}")

    print("\nALL_ADMIN_CONTRACT_OK")


if __name__ == "__main__":
    main()
