"""2026 中秋活动投放（运营数据，**不进种子**）。

背景：2026 年中秋节 = 9 月 25 日（周五），假期 9/25–9/27。本脚本以管理员身份，通过**后台接口**
（不是直插 SQL）投放一整套中秋运营物料，好处是所有校验/白名单/金额口径都真实生效：

  ① 分类「中秋团圆」        —— 活动与专区的作用域锚点
  ② 5 个中秋商品（带图）    —— 月饼礼盒 / 莲蓉蛋黄 / 苏式鲜肉 / 平和蜜柚 / 阳澄湖大闸蟹
  ③ 首页轮播主视觉（带图）  —— 置顶（sort_order=-1），点击进月饼礼盒
  ④ 公告（type=PROMOTION）  —— 置顶（sort_order=0 且发布最新 → 按 publishTime DESC 排第一）
  ⑤ 满减活动（限本分类）    —— 中秋团圆专区 满 99 减 15
  ⑥ 中秋专享券              —— 满 59 减 10，500 张
  ⑦ 限时秒杀                —— 月饼礼盒 ¥108（低于售价 ¥128），置顶
  ⑧ 3 条热搜词              —— 月饼 / 柚子 / 大闸蟹
  ⑨ 会员日                  —— 9 月 25 日（中秋当天）积分双倍

⚠️ 几个刻意的取舍（改之前先读）：
  · **活动只做「限本分类」而不是全场**：`ActivityService.evaluateBestActivity` 命中多个活动时
    **只取减免最大的那一个**（不叠加）。已有的「全场满200减50 / 满200打8折」在 200 元以上更优，
    本活动只在 99–199 元区间生效 —— 限分类能保证它只影响中秋商品，不会莫名其妙改动全站订单金额。
  · **不写进 `db/seed-data.sql`**：那是「每次启动自愈」的演示基线，`stock/price` 会被重置；
    运营物料进去就等于「改完一重启被静默还原」（公告/热搜词/热门标记都踩过）。本脚本是幂等的，要重投再跑一次即可。
  · **图片走 `POST /files/upload`**（本地磁盘模式，落 `backend/uploads/`），不直写磁盘 —— 这样
    URL 生成、大图压缩、类型校验都走既有链路。

用法：
  python deploy/place-midautumn-campaign.py            # 投放（已有同名则跳过，可重复跑）
  python deploy/place-midautumn-campaign.py --cleanup  # 反向清理（券没有删除接口 → 置为停用）
"""
import glob
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime

from _admin_token import temp_admin

BASE = "http://localhost:8080/api"
# 素材目录：优先环境变量，其次本机那份长期副本（工作区 midautumn-assets/），最后退回临时目录。
# ⚠️ 图片本体在 `backend/uploads/`（**已 gitignore**），所以换机器/新部署要重投时必须带上这些原图，
#    不能让它们只活在临时目录里（临时目录会被清理）。
_WS_ASSETS = r"C:\Users\asvs\WorkBuddy\2026-08-28-22-59-18\midautumn-assets"
IMG_DIR = (os.environ.get("SM_MIDAUTUMN_ASSETS")
           or (_WS_ASSETS if os.path.isdir(_WS_ASSETS) else os.path.join(os.environ.get("TEMP", "/tmp"), "sm-midautumn")))
YEAR = 2026
# 中秋假期：9/23（今天）开跑 → 9/27（假期最后一天）24 点收摊
# ⚠️ 时间必须 ISO-8601（**带 T**）：后端 Jackson 的 LocalDateTime 反序列化只认 "2026-09-23T00:00:00"，
#    空格分隔的 "2026-09-23 00:00:00" 会抛 InvalidFormatException → 且当前被兜底成 **HTTP 500 "Server error"**
#    （不是 400）。前台 datetime-local 控件天生给的就是带 T 的格式，所以只有手写脚本会踩。
START = f"{YEAR}-09-23T00:00:00"
END = f"{YEAR}-09-27T23:59:59"
MEMBER_DAY = f"{YEAR}-09-25"

# 分类
CATEGORY_NAME = "中秋团圆"
# 商品：(SKU, 名字, 图关键词, 售价, 原价, 会员价, 库存, 单位, 品牌, 副标题, 标签, 热门, 新品)
# ⚠️ sku 是**必填**（AdminProductCreateRequest 只标了 @Size，但后端仍校验非空 → 缺了报 400 "SKU is required"）
PRODUCTS = [
    ("MOONCAKE-GIFT-001", "中秋团圆月饼礼盒 8枚装", "月饼礼盒", 128.00, 168.00, 118.00, 200, "盒", "稻香村",
     "广式经典组合 · 赏月送礼首选", "中秋,月饼,礼盒", 1, 1),
    ("MOONCAKE-EGG-001", "广式莲蓉蛋黄月饼 2枚装", "莲蓉蛋黄", 25.80, 32.00, 23.90, 500, "份", "广州酒家",
     "流心咸蛋黄 · 细腻莲蓉", "中秋,月饼,蛋黄", 1, 0),
    ("MOONCAKE-MEAT-001", "苏式鲜肉月饼 6只装", "鲜肉月饼", 32.80, 39.90, 30.90, 300, "盒", "老盛昌",
     "现烤酥皮 · 上海老味道", "中秋,月饼,鲜肉", 0, 1),
    ("FRESH-POMELO-001", "福建平和红心蜜柚 2个装", "蜜柚", 19.90, 26.00, 18.50, 400, "份", "平和",
     "当季现摘 · 爆汁红心柚", "中秋,柚子,应季水果", 1, 0),
    ("FRESH-CRAB-001", "阳澄湖大闸蟹 公母对装", "大闸蟹", 199.00, 268.00, 189.00, 80, "对", "阳澄湖",
     "鲜活现发 · 中秋蟹礼", "中秋,大闸蟹,礼盒", 1, 1),
]
BANNER_IMG = "横幅主视觉"
BANNER_LINK = "中秋团圆月饼礼盒 8枚装"
FLASH_NAME = "中秋月饼礼盒 限时秒杀"
FLASH_PRODUCT = "中秋团圆月饼礼盒 8枚装"
FLASH_PRICE = 108.00
ANNOUNCEMENT = {
    "title": "中秋团圆节 · 专区满 99 减 15",
    "content": ("9 月 23 日至 27 日，中秋团圆专区单笔满 99 元立减 15 元；"
                "再叠加「中秋专享券」满 59 减 10 更划算。9 月 25 日中秋当天消费，积分双倍累计。"
                "月饼礼盒、平和蜜柚、阳澄湖大闸蟹均已上架，冷链配送次日达。"),
    "type": "PROMOTION",
}
ACTIVITY_NAME = "中秋团圆专区 满99减15"
COUPON_NAME = "中秋专享 满59减10"
HOT_WORDS = [("月饼", "中秋月饼"), ("柚子", "平和蜜柚"), ("大闸蟹", "阳澄湖大闸蟹")]

results = []
created = []          # (kind, id, 说明) —— 供 --cleanup 反查


def check(name, ok, detail=""):
    results.append((name, ok))
    print(("PASS " if ok else "FAIL ") + name + (" :: " + str(detail) if detail else ""))


def req(method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    r = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}


def data_of(resp):
    return resp.get("data") if isinstance(resp, dict) else None


def upload(token, file_path, biz_type, ascii_name):
    """走 /files/upload 上传（手工拼 multipart，避免引入 requests 依赖）。"""
    boundary = "----smb" + uuid.uuid4().hex
    ctype = "image/png" if file_path.lower().endswith(".png") else "image/jpeg"
    parts = [
        f"--{boundary}\r\n".encode(),
        f'Content-Disposition: form-data; name="file"; filename="{ascii_name}"\r\n'.encode(),
        f"Content-Type: {ctype}\r\n\r\n".encode(),
        open(file_path, "rb").read(),
        b"\r\n",
        f"--{boundary}--\r\n".encode(),
    ]
    r = urllib.request.Request(
        BASE + "/files/upload?type=" + biz_type, data=b"".join(parts), method="POST",
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}",
                 "Authorization": "Bearer " + token})
    try:
        with urllib.request.urlopen(r, timeout=60) as resp:
            return json.loads(resp.read().decode()).get("data", {}).get("url")
    except urllib.error.HTTPError as e:
        print("  上传失败 HTTP %s %s" % (e.code, e.read().decode()[:200]))
        return None


def find_image(keyword):
    hits = [p for p in glob.glob(os.path.join(IMG_DIR, "*.png")) if keyword in os.path.basename(p)]
    if len(hits) != 1:
        raise SystemExit(f"⚠️ 图片匹配「{keyword}」得到 {len(hits)} 个文件（应为 1），先检查 {IMG_DIR}")
    return hits[0]


# ---------------------------------------------------------------- 反查（幂等 + 清理共用）
def list_all(path, token, key="items"):
    st, body = req("GET", path, token)
    d = data_of(body)
    if isinstance(d, dict):
        return d.get(key) or d.get("records") or []
    return d or []


def find_by(path, token, predicate, query=""):
    for row in list_all(path + query, token):
        if predicate(row):
            return row
    return None


# ---------------------------------------------------------------- 投放
def place(token):
    print("=== 2026 中秋活动投放 ===\n")
    print(f"窗口：{START} → {END}（中秋 {MEMBER_DAY}）\n")

    # ---------- ① 分类 ----------
    cat = find_by("/admin/categories", token, lambda c: c.get("name") == CATEGORY_NAME)
    if cat:
        category_id = cat["id"]
        check("① 分类「中秋团圆」", True, f"已存在 id={category_id}（跳过创建）")
    else:
        st, body = req("POST", "/admin/categories", token,
                       {"parentId": 0, "name": CATEGORY_NAME, "sortNo": 5, "status": 1})
        category_id = (data_of(body) or {}).get("id")
        created.append(("category", category_id, CATEGORY_NAME))
        check("① 建分类「中秋团圆」", st == 200 and category_id, f"HTTP {st} id={category_id}")

    # ---------- ② 图片上传（**只为缺的物料上传**：幂等重跑不产生孤儿文件）----------
    def product_row(name):
        return find_by("/admin/products", token, lambda p, n=name: p.get("name") == n, "?size=100")

    existing_products = {p[1]: product_row(p[1]) for p in PRODUCTS}
    gift_row = existing_products.get(BANNER_LINK)
    banner_row = find_by("/admin/banners", token,
                         lambda b: gift_row and b.get("linkProductId") == gift_row["id"]) if gift_row else None

    needed = [p[2] for p in PRODUCTS if not existing_products[p[1]]]
    if not banner_row:
        needed.append(BANNER_IMG)

    images = {}
    if needed:
        for keyword in needed:
            path = find_image(keyword)
            biz = "banner" if keyword == BANNER_IMG else "product"
            ascii_name = ("midautumn-banner" if keyword == BANNER_IMG
                          else "midautumn-" + str(abs(hash(keyword)) % 10 ** 6)) + ".png"
            url = upload(token, path, biz, ascii_name)
            images[keyword] = url
            check(f"② 上传图片「{keyword}」", bool(url), url or "(失败)")
        if not all(images.values()):
            raise SystemExit("图片没传全，中止（避免建出没图的商品）")
    else:
        check("② 图片已就位（幂等重跑，未重复上传）", True, f"跳过 {len(PRODUCTS) + 1} 张")

    # ---------- ③ 商品 ----------
    product_ids = {}
    for sku, name, keyword, price, original, member, stock, unit, brand, subtitle, tags, hot, new in PRODUCTS:
        exist = existing_products[name]
        if exist:
            product_ids[name] = exist["id"]
            check(f"③ 商品「{name}」", True, f"已存在 id={exist['id']}（跳过）")
            continue
        cover = images[keyword]
        payload = {
            "categoryId": category_id, "sku": sku, "name": name, "subtitle": subtitle,
            "description": subtitle + "。中秋团圆节专供，产地直发、坏果包赔。",
            "coverUrl": cover, "price": price, "originalPrice": original, "memberPrice": member,
            "stock": stock, "lowStockThreshold": 10, "unit": unit, "status": "ON_SALE",
            "brand": brand, "isHot": hot, "isNew": new, "tags": tags,
            "images": [{"url": cover, "sortNo": 0}],
        }
        st, body = req("POST", "/admin/products", token, payload)
        pid = (data_of(body) or {}).get("id")
        product_ids[name] = pid
        created.append(("product", pid, name))
        check(f"③ 建商品「{name}」", st == 200 and pid,
              f"HTTP {st} id={pid} ¥{price}" if st == 200 else f"HTTP {st} {(body or {}).get('message', '')}")

    # ---------- ④ 轮播 ----------
    # 认「指向本次月饼礼盒的轮播」而不是认图片 URL（存储层用随机 uuid 命名，URL 每次上传都不一样）
    banner_url = images.get(BANNER_IMG) or (banner_row or {}).get("imageUrl")
    if banner_row:
        if banner_row.get("sortOrder") != -1 or not banner_row.get("enabled"):
            req("PUT", f"/admin/banners/{banner_row['id']}", token, {
                "imageUrl": banner_url, "linkProductId": product_ids[BANNER_LINK],
                "sortOrder": -1, "enabled": True})
        check("④ 首页轮播主视觉", True, f"已存在 id={banner_row['id']}（置顶已校正）")
    else:
        st, body = req("POST", "/admin/banners", token, {
            "imageUrl": banner_url, "linkProductId": product_ids[BANNER_LINK],
            "sortOrder": -1, "enabled": True})
        bid = (data_of(body) or {}).get("id")
        created.append(("banner", bid, "中秋主视觉"))
        check("④ 首页轮播主视觉（置顶）", st == 200 and bid, f"HTTP {st} id={bid}")

    # ---------- ⑤ 公告 ----------
    exist = find_by("/admin/announcements", token, lambda a: a.get("title") == ANNOUNCEMENT["title"], "?size=100")
    if exist:
        check("⑤ 中秋公告", True, f"已存在 id={exist['id']}（跳过）")
    else:
        st, body = req("POST", "/admin/announcements", token, {
            **ANNOUNCEMENT, "sortOrder": 0, "enabled": True})
        aid = (data_of(body) or {}).get("id")
        created.append(("announcement", aid, ANNOUNCEMENT["title"]))
        check("⑤ 中秋公告（PROMOTION）", st == 200 and aid, f"HTTP {st} id={aid}")

    # ---------- ⑥ 活动 ----------
    exist = find_by("/admin/activities", token, lambda a: a.get("name") == ACTIVITY_NAME, "?size=100")
    if exist:
        check("⑥ 满减活动", True, f"已存在 id={exist['id']}（跳过）")
    else:
        st, body = req("POST", "/admin/activities", token, {
            "name": ACTIVITY_NAME, "type": "FULL_REDUCTION", "scope": "CATEGORY",
            "categoryId": category_id, "threshold": 99, "discount": 15,
            "startTime": START, "endTime": END, "status": 1, "priority": 10})
        cid = (data_of(body) or {}).get("id")
        created.append(("activity", cid, ACTIVITY_NAME))
        check("⑥ 中秋专区 满99减15", st == 200 and cid, f"HTTP {st} id={cid} {(body or {}).get('message', '')}")

    # ---------- ⑦ 优惠券 ----------
    exist = find_by("/admin/coupons", token, lambda c: c.get("name") == COUPON_NAME, "?size=100")
    if exist:
        check("⑦ 中秋专享券", True, f"已存在 id={exist['id']}（跳过）")
    else:
        st, body = req("POST", "/admin/coupons", token, {
            "name": COUPON_NAME, "thresholdAmount": 59, "discountAmount": 10,
            "totalCount": 500, "startTime": START, "endTime": END})
        cpid = (data_of(body) or {}).get("id")
        created.append(("coupon", cpid, COUPON_NAME))
        check("⑦ 中秋专享券 满59减10", st == 200 and cpid, f"HTTP {st} id={cpid} {(body or {}).get('message', '')}")

    # ---------- ⑧ 秒杀 ----------
    exist = find_by("/admin/flash-sales", token, lambda f: f.get("name") == FLASH_NAME, "?size=100")
    if exist:
        check("⑧ 中秋秒杀", True, f"已存在 id={exist['id']}（跳过）")
    else:
        st, body = req("POST", "/admin/flash-sales", token, {
            "productId": product_ids[FLASH_PRODUCT], "name": FLASH_NAME, "flashPrice": FLASH_PRICE,
            "totalQuota": 100, "perUserLimit": 2,
            "startTime": START, "endTime": END, "status": 1, "sortNo": -1})
        fid = (data_of(body) or {}).get("id")
        created.append(("flashSale", fid, FLASH_NAME))
        check("⑧ 中秋秒杀（置顶）", st == 200 and fid, f"HTTP {st} id={fid} {(body or {}).get('message', '')}")

    # ---------- ⑨ 热搜词 ----------
    for i, (keyword, label) in enumerate(HOT_WORDS):
        exist = find_by("/admin/hot-searches", token, lambda h, k=keyword: h.get("keyword") == k)
        if exist:
            check(f"⑨ 热搜词「{label}」", True, f"已存在 id={exist['id']}（跳过）")
            continue
        st, body = req("POST", "/admin/hot-searches", token,
                       {"keyword": keyword, "label": label, "sortOrder": i - len(HOT_WORDS), "enabled": True})
        hid = (data_of(body) or {}).get("id")
        created.append(("hotSearch", hid, label))
        check(f"⑨ 热搜词「{label}」", st == 200 and hid, f"HTTP {st} id={hid}")

    # ---------- ⑩ 会员日 ----------
    exist = find_by("/admin/member-days", token, lambda d: str(d.get("memberDate"))[:10] == MEMBER_DAY)
    if exist:
        check("⑩ 中秋当天会员日", True, f"已存在 id={exist['id']}（跳过）")
    else:
        st, body = req("POST", "/admin/member-days", token, {
            "memberDate": MEMBER_DAY, "multiplier": 2, "remark": "中秋节", "enabled": True})
        mid = (data_of(body) or {}).get("id")
        created.append(("memberDay", mid, MEMBER_DAY))
        check("⑩ 中秋当天积分双倍", st == 200 and mid, f"HTTP {st} id={mid} {(body or {}).get('message', '')}")

    return product_ids, category_id


def cleanup(token, dry_run=False):
    """反向清理：先按名字查回 id，再删。

    ⚠️ 两处限制（脚本改不掉，属于后端能力缺口）：
      · **优惠券没有删除/停用以外的接口**（`AdminCouponController` 只有 GET/POST/PATCH status）→ 只能停用；
      · **分类要先把商品删空才能删**（外键）→ 顺序必须是 商品 → 分类。
    轮播没法按文件名匹配（存储层用随机 uuid 命名），所以按 `linkProductId` 指向本次新建的月饼礼盒来认。

    `dry_run=True` 只打印「会删哪些」——用它在**不撤掉活动**的前提下验证匹配逻辑是否命中
    （匹配写错的话会删错东西，这比不删更糟）。
    """
    print("=== 反向清理" + ("（dry-run：只打印，不删任何东西）" if dry_run else "") + " ===\n")

    def rows(path):
        return list_all(path + ("?size=100" if "?" not in path else ""), token)

    def do(method, path, what):
        """统一出口：dry-run 时只打印，绝不发写请求。"""
        if dry_run:
            print(f"  [将] {method} {path}  ← {what}")
            return 0
        st, _ = req(method, path, token)
        print(f"  {what} -> HTTP {st}")
        return st

    product_names = [p[1] for p in PRODUCTS]
    product_ids = {r["id"] for r in rows("/admin/products") if r.get("name") in product_names}

    # ① 轮播（按 linkProductId 命中本次的月饼礼盒）
    for b in rows("/admin/banners"):
        if b.get("linkProductId") in product_ids:
            do("DELETE", f"/admin/banners/{b['id']}", f"轮播 id={b['id']}")

    # ② 公告 / 活动 / 秒杀 / 热搜词（按名字）
    for label, path, field, targets in [
        ("公告", "/admin/announcements", "title", [ANNOUNCEMENT["title"]]),
        ("活动", "/admin/activities", "name", [ACTIVITY_NAME]),
        ("秒杀", "/admin/flash-sales", "name", [FLASH_NAME]),
        ("热搜词", "/admin/hot-searches", "keyword", [h[0] for h in HOT_WORDS]),
    ]:
        for r in rows(path):
            if r.get(field) in targets:
                do("DELETE", f"{path}/{r['id']}", f"{label}「{r.get(field)}」")

    # ③ 商品
    for r in rows("/admin/products"):
        if r.get("name") in product_names:
            do("DELETE", f"/admin/products/{r['id']}", f"商品「{r['name']}」")

    # ④ 券（只能停用）
    for r in rows("/admin/coupons"):
        if r.get("name") == COUPON_NAME:
            if dry_run:
                print(f"  [将] PATCH /admin/coupons/{r['id']}/status  ← 券「{COUPON_NAME}」停用")
            else:
                st, _ = req("PATCH", f"/admin/coupons/{r['id']}/status", token, {"status": 0})
                print(f"  券「{COUPON_NAME}」停用 -> HTTP {st}")

    # ⑤ 会员日
    for r in rows("/admin/member-days"):
        if str(r.get("memberDate"))[:10] == MEMBER_DAY:
            do("DELETE", f"/admin/member-days/{r['id']}", f"会员日 {MEMBER_DAY}")

    # ⑥ 分类（放最后：商品删完才能删）
    for r in rows("/admin/categories"):
        if r.get("name") == CATEGORY_NAME:
            do("DELETE", f"/admin/categories/{r['id']}", f"分类「{CATEGORY_NAME}」")


def main():
    cleanup_mode = "--cleanup" in sys.argv
    dry_run = "--dry-run" in sys.argv
    t0 = time.time()
    with temp_admin() as adm:
        if cleanup_mode:
            cleanup(adm.token, dry_run=dry_run)
        else:
            product_ids, category_id = place(adm.token)
            print()
            print("商品 id 映射：")
            for k, v in product_ids.items():
                print(f"  {v}\t{k}")
            print(f"分类 id：{category_id}")

    if not cleanup_mode:
        passed = sum(1 for _, ok in results if ok)
        print(f"\n==== {passed}/{len(results)} 项成功（{time.time() - t0:.1f}s）====")
        raise SystemExit(0 if passed == len(results) else 1)


if __name__ == "__main__":
    main()
