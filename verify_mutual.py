import json, urllib.request, urllib.error, ssl, time

BASE = "https://haomart.xyz/api"
CTX = ssl.create_default_context(); CTX.check_hostname = False; CTX.verify_mode = ssl.CERT_NONE

def req(method, path, token=None, body=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    r.add_header("Content-Type", "application/json"); r.add_header("Accept", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, context=CTX, timeout=30) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {"raw": e.read().decode()[:300]}

def get_items(j):
    d = j.get("data")
    if isinstance(d, dict):
        return d.get("items") or []
    if isinstance(d, list):
        return d
    return []

def fresh_user():
    ts = int(time.time()); U = f"mtest_{ts}"; P = ("138" + str(ts % 100000000).zfill(8))[:11]
    s, j = req("POST", "/auth/register", body={"username": U, "password": "Test123456", "phone": P, "nickname": "互斥测试"})
    assert s in (0, 200), f"register {s} {j}"
    s, j = req("POST", "/auth/login", body={"username": U, "password": "Test123456"}); tok = j["data"]["token"]
    return U, tok

def get_store(token):
    s, j = req("GET", "/stores", token); stores = j.get("data") or []
    st = next((x for x in stores if x.get("status") == 1), None) or (stores[0] if stores else None)
    return st.get("id") if st else None

def add(pid, qty, token):
    s, j = req("POST", "/cart/items", token, {"productId": pid, "quantity": qty})
    assert s in (0, 200), f"add {pid} {s} {j.get('message')}"

def get_cart(token):
    s, j = req("GET", "/cart", token); return j.get("data", {})

def receive_coupon(cid, token):
    s, j = req("POST", f"/coupons/{cid}/receive", token)
    assert s in (0, 200), f"receive {cid} {s} {j}"
    print("  receive coupon", cid, s, j.get("message"))

def mine_coupons(token):
    s, j = req("GET", "/coupons/mine", token); return get_items(j)

def create_order(token, item_ids, store_id, uc_id, remark):
    body = {"cartItemIds": item_ids, "fulfillmentType": "PICKUP", "pickupStoreId": store_id,
            "userCouponId": uc_id, "remark": remark}
    s, j = req("POST", "/orders", token, body)
    assert s in (0, 200), f"create order {s} {j}"
    return j.get("data", {})

def cancel_order(oid, token):
    if not oid:
        return
    s, j = req("POST", f"/orders/{oid}/cancel", token)
    st = j.get("data", {}).get("status") if isinstance(j.get("data"), dict) else j.get("data")
    print("  cancel order", oid, s, j.get("message"), "->", st)

print("=" * 70)
print("CLEANUP leftover order 1368 (user mtest_1792412690, holds flash quota)")
s, j = req("POST", "/auth/login", body={"username": "mtest_1792412690", "password": "Test123456"}); t0 = j["data"]["token"]
cancel_order(1368, t0)
print("=" * 70)

# ---------- ORDER A: flash line isolated + activity applies (no coupon) ----------
print("\n[A] flash-exclusion + activity (no coupon)")
U, tok = fresh_user(); store = get_store(tok)
add(183, 6, tok)          # 香酥鳕鱼块 39.90*6 = 239.40
add(161, 1, tok)          # 纯牛奶 限时秒杀 flashPrice 14.93 (perUserLimit 1)
cart = get_cart(tok); items = cart.get("items", [])
print("  cart act=%s nonFlash=%s selected=%s" % (cart.get("activityDiscount"), cart.get("nonFlashSelectedAmount"), cart.get("selectedAmount")))
for it in items:
    print("    pid=%s qty=%s sub=%s flash=%s" % (it.get("productId"), it.get("quantity"), it.get("subtotalAmount"), it.get("flashSaleId")))
ids = [it.get("id") for it in items]
odA = create_order(tok, ids, store, None, "A-闪隔+活动")
aA = float(odA.get("activityDiscount") or 0); cA = float(odA.get("discountAmount") or 0)
pA = float(odA.get("payAmount") or 0); tA = float(odA.get("totalAmount") or 0)
assert abs(float(cart.get("activityDiscount") or 0) - 50) < 0.01, "A activity should be 50"
assert abs(float(cart.get("nonFlashSelectedAmount")) - 239.40) < 0.01, "A nonFlash should be 239.40"
assert any(it.get("flashSaleId") for it in items), "A flash item missing flashSaleId"
assert not (aA > 0 and cA > 0), "A double subtract!"
assert abs(pA - (tA - aA)) < 0.01, f"A pay identity {pA} vs {tA - aA}"
print("  [A] PASS: flash excluded from activity (nonFlash=239.40, activity=50), pay=%.2f total=%.2f" % (pA, tA))
cancel_order(odA.get("id"), tok)

# ---------- ORDER B: coupon present, activity wins -> coupon NOT consumed ----------
print("\n[B] coupon present, activity wins (coupon must stay UNUSED)")
U, tok = fresh_user(); store = get_store(tok)
receive_coupon(1, tok)    # 新用户满50减10: discount 10, threshold 50 (usable)
mine = mine_coupons(tok)
cpB = next((c for c in mine if c.get("couponId") == 1), None)
ucB = cpB.get("id") if cpB else None
add(183, 6, tok)          # 239.40 -> activity 50 > coupon 10
cart = get_cart(tok); items = cart.get("items", [])
ids = [it.get("id") for it in items]
odB = create_order(tok, ids, store, ucB, "B-活动胜出券不消耗")
aB = float(odB.get("activityDiscount") or 0); cB = float(odB.get("discountAmount") or 0)
pB = float(odB.get("payAmount") or 0); tB = float(odB.get("totalAmount") or 0)
assert abs(aB - 50) < 0.01, f"B activity should be 50, got {aB}"
assert cB == 0, f"B coupon should NOT be applied (activity wins), got {cB}"
assert odB.get("userCouponId") in (None, 0, "0"), "B userCouponId should be null"
assert abs(pB - (tB - 50)) < 0.01, f"B pay identity {pB} vs {tB - 50}"
mine2 = mine_coupons(tok); cB2 = next((c for c in mine2 if c.get("id") == ucB), None)
assert cB2 and cB2.get("status") == "UNUSED", f"B coupon should remain UNUSED, got {cB2.get('status') if cB2 else None}"
print("  [B] PASS: activity wins, coupon NOT consumed (UNUSED), pay=%.2f total=%.2f" % (pB, tB))
cancel_order(odB.get("id"), tok)

# ---------- ORDER C: coupon wins (no applicable activity) ----------
print("\n[C] coupon wins (no activity on small cart) -> coupon consumed")
U, tok = fresh_user(); store = get_store(tok)
receive_coupon(3, tok)    # 日常满30减5: discount 5, threshold 30 (usable)
mine = mine_coupons(tok)
cpC = next((c for c in mine if c.get("couponId") == 3), None)
ucC = cpC.get("id") if cpC else None
add(183, 1, tok)          # 39.90 < 200 -> no FULL_REDUCTION/DISCOUNT; coupon 5 applies
cart = get_cart(tok); items = cart.get("items", [])
ids = [it.get("id") for it in items]
odC = create_order(tok, ids, store, ucC, "C-券胜出")
aC = float(odC.get("activityDiscount") or 0); cC = float(odC.get("discountAmount") or 0)
pC = float(odC.get("payAmount") or 0); tC = float(odC.get("totalAmount") or 0)
assert aC == 0, f"C activity should be 0 (subtotal<200), got {aC}"
assert abs(cC - 5) < 0.01, f"C coupon should be 5, got {cC}"
assert odC.get("userCouponId") == ucC, "C userCouponId should be set"
assert abs(pC - (tC - 5)) < 0.01, f"C pay identity {pC} vs {tC - 5}"
mine2 = mine_coupons(tok); cC2 = next((c for c in mine2 if c.get("id") == ucC), None)
assert cC2 and cC2.get("status") == "USED", f"C coupon should be USED, got {cC2.get('status') if cC2 else None}"
print("  [C] PASS: coupon wins, coupon consumed (USED), activity 0, pay=%.2f total=%.2f" % (pC, tC))
cancel_order(odC.get("id"), tok)

print("\n" + "=" * 70)
print("RESULT: ALL MUTUAL-EXCLUSION CHECKS PASSED")
print("  A) flash line isolated from activity + no double-subtract")
print("  B) activity > coupon -> coupon NOT consumed (UNUSED), pay=total-activity")
print("  C) coupon > activity -> coupon consumed (USED), activity 0, pay=total-coupon")
