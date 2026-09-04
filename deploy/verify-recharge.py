import json, urllib.request, urllib.error

BASE = "http://localhost:8080/api"

def req(method, path, token=None, body=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=10) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {"raw": e.read().decode()}

# login as a normal user (register one if needed)
s, j = req("POST", "/auth/login", body={"username": "recharge_test", "password": "recharge123"})
if s != 200 or j.get("code") != 0:
    s, j = req("POST", "/auth/register", body={"username": "recharge_test", "password": "recharge123", "nickname": "充值测试"})
    print("register recharge_test:", s, j.get("code"))
    s, j = req("POST", "/auth/login", body={"username": "recharge_test", "password": "recharge123"})
assert s == 200 and j.get("code") == 0, f"login failed: {s} {j}"
token = j["data"]["token"]
uid = j["data"]["user"]["id"]
print("login OK uid=", uid)

# wallet before
s, j = req("GET", "/wallet", token)
before = float(j["data"]["balance"])
print("balance before =", before)

# 1. create recharge order (ALIPAY, 100)
s, j = req("POST", "/wallet/recharge-orders", token, {"amount": 100, "method": "ALIPAY"})
assert s == 200 and j.get("code") == 0, f"create failed: {s} {j}"
order = j["data"]
oid = order["id"]
print("created order id=", oid, "status=", order["status"], "expireAt(ms)=", order["expireAt"], "remaining(s)=", order["remainingSeconds"])
assert order["status"] == "PENDING"
assert order["remainingSeconds"] > 0

# 2. get order
s, j = req("GET", f"/wallet/recharge-orders/{oid}", token)
assert s == 200 and j.get("code") == 0 and j["data"]["status"] == "PENDING"

# 3. pay order
s, j = req("POST", f"/wallet/recharge-orders/{oid}/pay", token)
assert s == 200 and j.get("code") == 0, f"pay failed: {s} {j}"
print("after pay status =", j["data"]["status"])
assert j["data"]["status"] == "PAID"

# 4. balance credited
s, j = req("GET", "/wallet", token)
after = float(j["data"]["balance"])
print("balance after =", after, "delta =", round(after - before, 2))
assert abs((after - before) - 100) < 0.01, "balance not credited correctly"

# 5. cannot pay again (already PAID -> 409)
s, j = req("POST", f"/wallet/recharge-orders/{oid}/pay", token)
print("re-pay status =", s, "msg =", j.get("message"))
assert s != 200 or j.get("code") != 0

# 6. cancel path on a fresh order
s, j = req("POST", "/wallet/recharge-orders", token, {"amount": 50, "method": "WECHAT"})
oid2 = j["data"]["id"]
print("created 2nd order id=", oid2, "method=", j["data"]["method"])
s, j = req("POST", f"/wallet/recharge-orders/{oid2}/cancel", token)
print("cancel 2nd order status =", s, j.get("code"), j.get("message"))
assert s == 200 and j.get("code") == 0 and j["data"]["status"] == "CANCELLED"
s, j = req("GET", "/wallet", token)
assert abs(float(j["data"]["balance"]) - after) < 0.01, "cancel should NOT change balance"

# 7. bad method rejected
s, j = req("POST", "/wallet/recharge-orders", token, {"amount": 10, "method": "PAYPAL"})
print("bad method status =", s, "msg =", j.get("message"))
assert s != 200 or j.get("code") != 0

print("\nRESULT: recharge flow PASSED ✅")
