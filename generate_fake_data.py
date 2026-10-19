# -*- coding: utf-8 -*-
"""
确定性「活人感」数据生成器 —— 单一真相派生，保证 7 条一致性规则。

设计要点：
- 钱包余额 = 该用户 wallet_transaction 全部 SUCCESS 流水代数和（RECHARGE/REFUND +，PAYMENT -），
  每笔 balance_before/after 首尾相接、全部 ≥0；绝不单独写死余额（规则①）。
- 流水与单据 1:1：PAYMENT↔已支付订单、REFUND↔退款终态订单、RECHARGE↔recharge_order(PAID)（规则②）。
- 订单金额自洽：pay_amount = total+freight−discount；discount=activity+points+coupon；
  member_discount 恒 0；Σ subtotal = total；subtotal = price×qty（规则③）。
- payment_record 与订单一致：每笔已支付订单恰一条，status 跟随（规则④）。
- 库存/销量与订单一致：stock = baseline − Σ(已支付/锁单 qty)；sales = Σ(已支付 qty)；秒杀商品不动（规则⑤）。
- 积分一致：user.points = point_ledger 净额；订单 points_earned/used 与 ledger 对应（规则⑥）。
- 管理端聚合 = 底层实时聚合（生成器不写任何缓存；规则⑦由后端保证，此处只保证底层自洽）。

幂等：每次运行先 DELETE 再 INSERT，最终态一致。
安全：必须设置环境变量 APPLY=1 才真正写库，否则只打印统计（DRY RUN）。
"""
import os
import sys
import re
import json
import random
import pymysql
from decimal import Decimal, ROUND_HALF_UP


def _load_seed_baseline():
    """从 seed-data.sql 解析商品原始 stock（库存基线），缓存到 _product_seed_baseline.json。
    种子库存是库存/销量的唯一真相起点：每次运行都从种子库存派生，避免把上一轮写回的耗尽库存当基线。"""
    cache = os.path.join(os.path.dirname(os.path.abspath(__file__)), "_product_seed_baseline.json")
    if os.path.exists(cache):
        with open(cache, encoding="utf-8") as f:
            return {int(k): int(v) for k, v in json.load(f).items()}
    seed_sql = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                            "backend", "src", "main", "resources", "db", "seed-data.sql")
    with open(seed_sql, encoding="utf-8", errors="replace") as f:
        text = f.read()
    m = re.search(r"INSERT INTO `product`\s*\(([^)]+)\)\s*VALUES\s*\((.*)\)\s*ON DUPLICATE", text, re.S)
    cols = [c.strip().strip("`") for c in m.group(1).split(",")]
    si = cols.index("stock")
    block = m.group(2)
    baseline = {}
    for tup in re.split(r"\)\s*,\s*\(", block):
        parts = tup.split(",")
        if len(parts) <= si:
            continue
        try:
            pid = int(parts[0].strip())
            baseline[pid] = int(parts[si].strip())
        except (ValueError, IndexError):
            continue
    with open(cache, "w", encoding="utf-8") as f:
        json.dump(baseline, f)
    return baseline

SEED = 20261010
KEEP_IDS = [1, 21]            # admin + zzm
NEW_USER_BASE = 5000
NEW_USER_COUNT = 98           # 总用户 ~100
START_DATE = "2026-04-01 00:00:00"
END_DATE = "2026-10-09 23:59:59"
APPLY = os.environ.get("APPLY") == "1"

DB = dict(host=os.environ.get("DB_HOST", "127.0.0.1"),
          port=int(os.environ.get("DB_PORT", "3306")),
          user=os.environ.get("DB_USER", "root"),
          password=os.environ.get("DB_PASSWORD", "zzmsgz"),
          database=os.environ.get("DB_NAME", "supermarket_system"),
          charset="utf8mb4", autocommit=False)

TIER_THRESHOLDS = [0, 1000, 5000, 20000, 30000, 50000, 100000]

PROVINCES = [("北京市", "北京市", "朝阳区"), ("上海市", "上海市", "浦东新区"),
             ("广东省", "广州市", "天河区"), ("浙江省", "杭州市", "西湖区"),
             ("江苏省", "南京市", "鼓楼区"), ("四川省", "成都市", "武侯区"),
             ("湖北省", "武汉市", "洪山区"), ("陕西省", "西安市", "雁塔区"),
             ("山东省", "青岛市", "市南区"), ("福建省", "厦门市", "思明区")]
SURNAMES = "王李张刘陈杨黄赵周吴徐孙马朱胡郭何高林罗郑梁谢宋唐许韩冯邓曹彭"
GIVEN = ["伟","芳","娜","秀英","敏","静","丽","强","磊","军","洋","勇","艳","杰","娟","涛","明","超","霞","平","刚","桂英"]
STREETS = ["幸福路","和平街","建设路","人民大道","阳光小区","锦绣花园","滨江路","和谐苑","世纪城","文苑路"]
REVIEW_GOOD = [
    "东西很好，物流也快，下次还来。", "质量超出预期，包装严实，满意！", "价格实惠，性价比高，推荐购买。",
    "收到了，和描述一致，好评。", "新鲜度不错，送货上门很方便。", "用了几天才来评价，确实好用。",
    "客服态度好，解决问题及时。", "分量足，日期新鲜，会回购。", "比超市便宜还送到家，香！",
    "包装完整无破损，体验很好。", "第二次买了，品质稳定。", "味道正宗，家人都喜欢。"]
REVIEW_MID = [
    "整体还行，就是物流稍微慢了点。", "商品可以，包装再严实点就好了。", "一般般，谈不上惊艳但也不差。",
    "有点小瑕疵，不过不影响使用。", "价格适中，质量中规中矩。"]
REVIEW_BAD = [
    "和图片有点出入，希望改进。", "到货时包装有点压扁了。", "口感一般，可能不适合我。"]
MSG_TITLES = [("订单通知", "您的订单已发货，请留意查收。"),
              ("促销活动", "限时秒杀进行中，更多好物等你来抢。"),
              ("积分提醒", "您的积分已到账，可在结算时抵扣现金。"),
              ("会员专享", "会员日双倍积分，记得消费哦。"),
              ("物流提醒", "您的包裹已送达，请确认收货。")]


def D(x):
    return Decimal(str(x)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def tier_of(total_spent):
    t = Decimal(total_spent)
    lvl = 0
    for i, th in enumerate(TIER_THRESHOLDS):
        if t >= Decimal(th):
            lvl = i
    return lvl


def fmt(dt):
    return dt.strftime("%Y-%m-%d %H:%M:%S")


def main():
    conn = pymysql.connect(**DB)
    cur = conn.cursor()

    # ---- 读取种子数据 ----
    seed_baseline = _load_seed_baseline()
    cur.execute("SELECT id, name, sku, price, original_price, stock, sales, cover_url, unit, is_hot, is_new "
                "FROM product WHERE deleted=0 AND (kind IS NULL OR kind='NORMAL')")
    products = []
    baseline = {}
    for r in cur.fetchall():
        pid, name, sku, price, oprice, stock, sales, cover, unit, hot, nw = r
        price = Decimal(price)
        oprice = Decimal(oprice) if oprice is not None else None
        stock = int(stock)
        # 用种子库存当基线（而非上一轮写回的耗尽库存），保证可重复运行且规模稳定
        baseline[pid] = seed_baseline.get(pid, stock)
        products.append(dict(id=pid, name=name, sku=sku, price=price, oprice=oprice,
                             cover=cover or "", unit=unit or "", hot=hot, nw=nw,
                             weight=1 + (2 if hot else 0) + (1 if nw else 0)
                                     + min(int(sales or 0), 2000) / 400))
    cur.execute("SELECT id, name, threshold_amount, discount_amount FROM coupon WHERE status=1 AND deleted=0")
    coupons = [dict(id=c[0], name=c[1], th=Decimal(c[2]), disc=Decimal(c[3])) for c in cur.fetchall()]
    cur.execute("SELECT id, username FROM sys_user WHERE username IN ('admin','zzm')")
    keep_rows = cur.fetchall()
    keep_ids = [r[0] for r in keep_rows]
    KEEP_IDS_local = keep_ids
    # 复用已有用户的 password_hash（Test123456）
    cur.execute("SELECT password_hash FROM sys_user WHERE role='USER' LIMIT 1")
    pw_hash = cur.fetchone()[0]

    if not APPLY:
        print("========== DRY RUN（未写库，设置 APPLY=1 才真正写入）==========")

    # ---- 删除旧数据（幂等前提）----
    if APPLY:
        del_ids_sub = ",".join(str(i) for i in KEEP_IDS_local)
        # 非保留用户：连根删除（先删子表再删父表，规避外键约束）
        cur.execute(f"DELETE FROM wallet_transaction WHERE user_id NOT IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM recharge_order WHERE user_id NOT IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM product_review WHERE order_id IN (SELECT id FROM orders WHERE user_id NOT IN ({del_ids_sub}))")
        cur.execute(f"DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE user_id NOT IN ({del_ids_sub}))")
        cur.execute(f"DELETE FROM payment_record WHERE order_id IN (SELECT id FROM orders WHERE user_id NOT IN ({del_ids_sub}))")
        cur.execute(f"DELETE FROM return_request WHERE order_id IN (SELECT id FROM orders WHERE user_id NOT IN ({del_ids_sub}))")
        cur.execute(f"UPDATE orders SET user_coupon_id=NULL WHERE user_id NOT IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM user_coupon WHERE order_id IN (SELECT id FROM orders WHERE user_id NOT IN ({del_ids_sub}))")
        cur.execute(f"DELETE FROM stock_log WHERE order_id IN (SELECT id FROM orders WHERE user_id NOT IN ({del_ids_sub})) OR operator_id NOT IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM orders WHERE user_id NOT IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM point_ledger WHERE user_id NOT IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM user_coupon WHERE user_id NOT IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM user_favorite WHERE user_id NOT IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM cart_item WHERE user_id NOT IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM user_address WHERE user_id NOT IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM user_message WHERE user_id NOT IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM page_dwell WHERE user_id NOT IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM sys_user WHERE id NOT IN ({del_ids_sub})")
        # 保留用户：清掉其依赖数据（重建），保留 sys_user 行
        cur.execute(f"DELETE FROM wallet_transaction WHERE user_id IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM recharge_order WHERE user_id IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM product_review WHERE order_id IN (SELECT id FROM orders WHERE user_id IN ({del_ids_sub}))")
        cur.execute(f"DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE user_id IN ({del_ids_sub}))")
        cur.execute(f"DELETE FROM payment_record WHERE order_id IN (SELECT id FROM orders WHERE user_id IN ({del_ids_sub}))")
        cur.execute(f"DELETE FROM return_request WHERE order_id IN (SELECT id FROM orders WHERE user_id IN ({del_ids_sub}))")
        cur.execute(f"UPDATE orders SET user_coupon_id=NULL WHERE user_id IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM user_coupon WHERE order_id IN (SELECT id FROM orders WHERE user_id IN ({del_ids_sub}))")
        cur.execute(f"DELETE FROM stock_log WHERE order_id IN (SELECT id FROM orders WHERE user_id IN ({del_ids_sub})) OR operator_id IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM orders WHERE user_id IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM point_ledger WHERE user_id IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM user_coupon WHERE user_id IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM user_favorite WHERE user_id IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM cart_item WHERE user_id IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM user_address WHERE user_id IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM user_message WHERE user_id IN ({del_ids_sub})")
        cur.execute(f"DELETE FROM page_dwell WHERE user_id IN ({del_ids_sub})")
        # 所有订单都将被重新生成，订单子表里的旧行（含孤儿行）一律作废，避免破坏一致性断言
        cur.execute("DELETE FROM payment_record")
        cur.execute("DELETE FROM return_request")
        cur.execute("DELETE FROM order_item")
        cur.execute("DELETE FROM product_review")
        cur.execute("DELETE FROM user_coupon")

    # ---- 计数器（从库里各表当前最大 ID + 1 起算，杜绝主键碰撞，兼容孤儿行）----
    def _maxid(table):
        cur.execute(f"SELECT COALESCE(MAX(id),0) FROM `{table}`")
        return int(cur.fetchone()[0])
    _m = {t: _maxid(t) for t in ["sys_user","orders","order_item","wallet_transaction",
                                  "recharge_order","payment_record","point_ledger","product_review",
                                  "return_request","user_coupon","user_favorite","cart_item",
                                  "user_address","user_message","page_dwell"]}
    oid = _m["orders"]; iid = _m["order_item"]; wid = _m["wallet_transaction"]
    rcid = _m["recharge_order"]; pidr = _m["payment_record"]; plid = _m["point_ledger"]
    rvid = _m["product_review"]; rrid = _m["return_request"]; ucid = _m["user_coupon"]
    favid = _m["user_favorite"]; cartid = _m["cart_item"]; addrid = _m["user_address"]
    msgid = _m["user_message"]; dwellid = _m["page_dwell"]

    orders_rows = []; item_rows = []; wallet_rows = []; recharge_rows = []
    payment_rows = []; point_rows = []; review_rows = []; return_rows = []
    ucoupon_rows = []; fav_rows = []; cart_rows = []; addr_rows = []; msg_rows = []; dwell_rows = []
    new_user_rows = []

    user_stats = {}   # uid -> dict(balance, points, total_spent, member_level, nickname)
    avail = dict(baseline)   # 剩余可售库存（按 baseline 起算）
    prod_sales = {}          # pid -> 累计已支付销量
    coupon_used = {}         # coupon_id -> 使用次数

    from datetime import datetime, timedelta
    start_dt = datetime.strptime(START_DATE, "%Y-%m-%d %H:%M:%S")
    end_dt = datetime.strptime(END_DATE, "%Y-%m-%d %H:%M:%S")

    def rand_dt(rng, lo, hi):
        span = int((hi - lo).total_seconds())
        return lo + timedelta(seconds=rng.randint(0, max(1, span)))

    # 用户列表：保留(admin,zzm) + 新用户
    user_ids = []
    # 保留用户
    for uid in KEEP_IDS_local:
        user_stats[uid] = dict(balance=0, points=0, total_spent=0, member_level=0, nickname="")
        user_ids.append(uid)
    # 新用户
    new_uid = NEW_USER_BASE
    for k in range(NEW_USER_COUNT):
        rng0 = random.Random(SEED * 7 + new_uid)
        uid = new_uid
        new_uid += 1
        surname = rng0.choice(SURNAMES)
        given = rng0.choice(GIVEN)
        nick = surname + given
        phone = "1" + str(rng0.randint(30, 89)) + "".join(str(rng0.randint(0, 9)) for _ in range(8))
        email = f"user{uid}@example.com"
        gender = rng0.choice(["男", "女"])
        by = rng0.randint(1975, 2005)
        bm = rng0.randint(1, 12)
        bd = rng0.randint(1, 28)
        birthday = f"{by}-{bm:02d}-{bd:02d}"
        new_user_rows.append((uid, f"shopper_{uid}", pw_hash, nick,
                              f"/seed-avatars/av-{uid % 20 + 1:02d}.jpg",
                              phone, email, gender,
                              birthday, "USER", 1, 0, 0, 0, 0, 0,
                              fmt(rand_dt(rng0, start_dt, end_dt - timedelta(days=30))), fmt(end_dt), 0))
        user_stats[uid] = dict(balance=0, points=0, total_spent=0, member_level=0, nickname=nick)
        user_ids.append(uid)

    # 产品加权抽样
    prod_ids = [p["id"] for p in products]
    prod_by_id = {p["id"]: p for p in products}
    weights = [p["weight"] for p in products]

    def pick_product(rng, avail_map=None):
        # 排除库存为 0 的商品；avail_map 传本单快照以控制单笔订单内不超卖
        am = avail_map if avail_map is not None else avail
        cand = [(p["id"], p["weight"]) for p in products if am[p["id"]] >= 1]
        if not cand:
            return None
        ids = [c[0] for c in cand]
        ws = [c[1] for c in cand]
        return rng.choices(ids, weights=ws, k=1)[0]

    # ---- 逐用户生成时间线 ----
    total_orders = 0
    for uid in user_ids:
        rng = random.Random(SEED * 1000 + uid)
        stats = user_stats[uid]
        n_orders = rng.randint(6, 20)
        # 预生成订单时间戳（排序）
        order_times = sorted(rand_dt(rng, start_dt, end_dt) for _ in range(n_orders))
        # 初始充值 1-3 笔（早于首单）
        recharge_specs = []
        for _ in range(rng.randint(1, 3)):
            rt = start_dt + timedelta(days=rng.randint(0, 10)) + timedelta(hours=rng.randint(0, 23))
            if rt >= order_times[0]:
                rt = order_times[0] - timedelta(days=1, hours=rng.randint(1, 12))
            amt = D(rng.choice([100, 200, 300, 500, 1000, 2000]))
            recharge_specs.append((rt, amt))
        # 合并事件并按时间排序
        events = []
        for rt, amt in recharge_specs:
            events.append((rt, "recharge", amt))
        for ot in order_times:
            events.append((ot, "order", None))
        events.sort(key=lambda e: e[0])

        bal = 0
        pts = 0
        spent = 0
        # 保证时间戳严格递增（每用户）
        prev_ts = None
        used_review_items = set()
        used_cp = set()         # 本用户已领券，避免 (user,coupon) 唯一约束冲突

        def emit_wallet(ts, ttype, order_id, amount, remark):
            nonlocal bal, wid
            bb = bal
            if ttype in ("RECHARGE", "REFUND"):
                bal = bal + amount
            else:
                bal = bal - amount
            ba = bal
            wid += 1
            txno = f"TX{ts.strftime('%Y%m%d%H%M%S')}{wid:06d}"
            wallet_rows.append((wid, txno, uid, order_id, ttype, amount, D(bb), D(ba), "SUCCESS", remark, fmt(ts)))

        def emit_recharge(ts, amount):
            nonlocal rcid, wid
            rcid += 1
            order_no = f"RC{ts.strftime('%Y%m%d%H%M%S')}{rcid:06d}"
            expire = ts + timedelta(days=30)
            recharge_rows.append((rcid, order_no, uid, amount, "WECHAT", "PAID", fmt(expire), fmt(ts), fmt(ts), fmt(ts)))
            emit_wallet(ts, "RECHARGE", None, amount, "账户充值")

        for ev in events:
            ts, kind, amt = ev
            # 严格递增
            if prev_ts is not None and ts <= prev_ts:
                ts = prev_ts + timedelta(seconds=1)
            prev_ts = ts

            if kind == "recharge":
                emit_recharge(ts, amt)
                continue

            # ---- 构建订单 ----
            # 选 1-4 件商品（用本单库存快照，选一个立即递减，防止单笔订单内对同一商品超卖）
            n_items = rng.randint(1, 4)
            chosen = []
            order_avail = dict(avail)
            for _ in range(n_items):
                pid = pick_product(rng, order_avail)
                if pid is None:
                    break
                maxq = min(5, order_avail[pid])
                if maxq < 1:
                    continue
                qty = rng.randint(1, max(1, maxq))
                chosen.append((pid, qty))
                order_avail[pid] -= qty
            if not chosen:
                continue  # 没货可卖，跳过

            # 生命周期
            roll = rng.random()
            if roll < 0.70:
                lifecycle = "COMPLETED"
            elif roll < 0.82:
                lifecycle = "SHIPPED"
            elif roll < 0.90:
                lifecycle = "REFUNDED"
            elif roll < 0.96:
                lifecycle = "PENDING_PAYMENT"
            else:
                lifecycle = "CANCELLED"
            paid = lifecycle in ("COMPLETED", "SHIPPED", "REFUNDED")
            # orders.status 合法集合：PENDING_PAYMENT/PAID/SHIPPED/COMPLETED/CANCELED/CLOSED
            # （无 REFUNDED 这个值；退款用 refund_status='APPROVED' 表达；CANCELLED 拼写是单 L）
            order_status = "COMPLETED" if lifecycle == "REFUNDED" else (
                "CANCELED" if lifecycle == "CANCELLED" else lifecycle)

            # 计算金额
            subtotal = D(0)
            item_specs = []
            for pid, qty in chosen:
                p = prod_by_id[pid]
                unit_price = p["price"]
                st = D(unit_price * qty)
                subtotal = subtotal + st
                item_specs.append((pid, qty, unit_price, st, p))

            # 活动折扣
            activity_id = None; activity_name = None; activity_disc = D(0)
            if subtotal >= 200 and rng.random() < 0.5:
                if rng.random() < 0.5:
                    activity_id, activity_name, activity_disc = 26, "全场满200减50", D(50)
                else:
                    activity_id, activity_name, activity_disc = 27, "全场满200打8折", D(subtotal * Decimal("0.2"))

            # 积分抵扣（仅实际支付时扣除，使用已有积分）
            points_used = 0; points_disc = D(0); redeem_idx = None
            if paid and pts >= 100 and rng.random() < 0.25:
                cand = min(pts, rng.choice([100, 200, 500, 1000]))
                points_used = cand
                points_disc = D(cand * Decimal("0.01"))
                pts -= cand
                plid += 1
                redeem_idx = len(point_rows)
                point_rows.append((plid, uid, "REDEEM", -cand, pts,
                                   None, "下单积分抵扣", fmt(ts)))

            # 优惠券（后抵扣，封顶不超应付）
            coupon_id = None; coupon_name = None; coupon_disc = D(0); uc_id = None
            for cp in coupons:
                if cp["id"] in used_cp:
                    continue
                if subtotal >= cp["th"] and rng.random() < 0.4:
                    coupon_disc = cp["disc"]
                    coupon_id = cp["id"]; coupon_name = cp["name"]
                    ucid += 1
                    received = ts - timedelta(days=rng.randint(1, 20))
                    uc_id = ucid
                    ucoupon_rows.append((ucid, cp["id"], uid, "USED", oid + 1,
                                         fmt(received), fmt(ts), fmt(received), fmt(ts)))
                    coupon_used[cp["id"]] = coupon_used.get(cp["id"], 0) + 1
                    used_cp.add(cp["id"])
                    break

            discount = activity_disc + points_disc + coupon_disc
            freight = D(0) if subtotal >= 99 else D(6)
            pay_amount = subtotal + freight - discount
            if pay_amount < 1:   # 折扣过大，收缩优惠券折扣
                coupon_disc = max(D(0), coupon_disc - (1 - pay_amount))
                discount = activity_disc + points_disc + coupon_disc
                pay_amount = subtotal + freight - discount
            if pay_amount < 1:
                pay_amount = D(1)

            # 时间字段
            created = ts
            paid_at = None; shipped_at = None; completed_at = None; canceled_at = None
            refunded_at = None; return_created = None; return_approved = None; return_completed = None
            if paid:
                paid_at = created + timedelta(hours=rng.randint(0, 6))
                if lifecycle in ("SHIPPED", "COMPLETED", "REFUNDED"):
                    shipped_at = paid_at + timedelta(days=rng.randint(1, 3))
                if lifecycle in ("COMPLETED", "REFUNDED"):
                    completed_at = shipped_at + timedelta(days=rng.randint(1, 6))
                if lifecycle == "REFUNDED":
                    return_created = completed_at + timedelta(days=rng.randint(2, 10))
                    return_approved = return_created + timedelta(days=rng.randint(1, 3))
                    return_completed = return_approved + timedelta(days=rng.randint(1, 2))
                    refunded_at = return_completed
            else:
                if lifecycle == "CANCELLED":
                    canceled_at = created + timedelta(hours=rng.randint(1, 48))

            # 余额不足则先充值补足（时间戳 = 订单前 1 秒）
            if paid and bal < pay_amount:
                need = pay_amount - bal + D(rng.choice([10, 50, 100]))
                cover_ts = created - timedelta(seconds=1)
                emit_recharge(cover_ts, D(need))

            # 写入订单行
            oid += 1
            order_no = f"NO{created.strftime('%Y%m%d%H%M%S')}{oid:06d}"
            # 回填积分抵扣 ledger 的 order_id（产出时 oid 尚未分配）
            if redeem_idx is not None:
                row = point_rows[redeem_idx]
                point_rows[redeem_idx] = row[:5] + (oid,) + row[6:]
            member_lv = stats["member_level"]
            updated = max(t for t in [created, paid_at, shipped_at, completed_at, canceled_at, refunded_at] if t is not None)
            # 快递公司/单号：已发货（SHIPPED/COMPLETED，REFUNDED 也走 COMPLETED）才有物流。
            # 按 oid 确定性派生、不消耗 rng——保证与其余数据基线完全一致；
            # 存量库回填 SQL（deploy 时一次性 UPDATE）必须与本公式保持同一结果。
            _CARRIERS = (("顺丰速运", "SF"), ("中通快递", "ZTO"), ("圆通速运", "YTO"),
                         ("韵达快递", "YD"), ("京东物流", "JD"), ("邮政EMS", "EMS"))
            ship_company = ship_no = None
            if order_status in ("SHIPPED", "COMPLETED"):
                _cname, _cprefix = _CARRIERS[oid % len(_CARRIERS)]
                ship_company = _cname
                ship_no = f"{_cprefix}{oid * 977 + 13:013d}"
            orders_rows.append((
                oid, order_no, uid, D(subtotal), freight, D(discount), D(pay_amount),
                order_status, ("PAID" if paid else "UNPAID"),
                f"收货人{uid}", f"13{rng.randint(100000000,999999999)}",
                f"{rng.choice(PROVINCES)[0]}{rng.choice(PROVINCES)[1]}{rng.choice(PROVINCES)[2]}{rng.choice(STREETS)}{rng.randint(1,99)}号",
                "", ship_company, ship_no,
                ("APPROVED" if lifecycle == "REFUNDED" else "NONE"), None, None,
                (fmt(refunded_at) if refunded_at else None),
                None, coupon_name,
                (fmt(paid_at) if paid_at else None),
                (fmt(shipped_at) if shipped_at else None),
                (fmt(completed_at) if completed_at else None),
                (fmt(canceled_at) if canceled_at else None),
                None, fmt(created), fmt(updated),
                activity_id, activity_disc, activity_name, D(0),
                points_disc, points_used, 0, member_lv,
                "DELIVERY", None, None, None, None))

            # 订单项
            for (pid, qty, unit_price, st, p) in item_specs:
                iid += 1
                item_rows.append((
                    iid, oid, pid, p["name"], p["sku"], p["cover"], unit_price,
                    p["oprice"], None, qty, st, ""))

            # 库存/销量（仅在消费型状态扣减；退款/取消净 0）
            if lifecycle in ("PENDING_PAYMENT", "PAID", "SHIPPED", "COMPLETED"):
                for (pid, qty, *_rest) in item_specs:
                    avail[pid] -= qty
                    if lifecycle in ("PAID", "SHIPPED", "COMPLETED"):
                        prod_sales[pid] = prod_sales.get(pid, 0) + qty

            # 支付流水 + 积分发放 + 支付记录
            if paid:
                emit_wallet(created if paid_at is None else paid_at, "PAYMENT", oid, D(pay_amount), "商品支付")
                pidr += 1
                pay_no = f"PAY{paid_at.strftime('%Y%m%d%H%M%S') if paid_at else created.strftime('%Y%m%d%H%M%S')}{pidr:06d}"
                pay_status = "REFUNDED" if lifecycle == "REFUNDED" else "SUCCESS"
                payment_rows.append((pidr, oid, pay_no, rng.choice(["WECHAT", "ALIPAY"]),
                                     D(pay_amount), pay_status,
                                     fmt(paid_at) if paid_at else fmt(created), fmt(created), fmt(updated)))
                # 积分发放（仅实际成交且非退款）
                earned = 0
                if lifecycle in ("COMPLETED", "SHIPPED"):
                    earned = int(pay_amount)  # 1 元 = 1 分
                    pts += earned
                    plid += 1
                    point_rows.append((plid, uid, "EARN", earned, pts, oid, "下单获得积分",
                                       fmt(paid_at) if paid_at else fmt(created)))
                    # 写回订单 points_earned
                    orders_rows[-1] = orders_rows[-1][:34] + (earned,) + orders_rows[-1][35:]
                spent = spent + pay_amount

            # 退款：退款流水 + 退回已抵积分 + 退货记录
            if lifecycle == "REFUNDED":
                emit_wallet(refunded_at, "REFUND", oid, D(pay_amount), "订单退款")
                if points_used > 0:
                    pts += points_used
                    plid += 1
                    point_rows.append((plid, uid, "REFUND", points_used, pts, oid, "退款返还抵扣积分", fmt(refunded_at)))
                rrid += 1
                need_return = rng.random() < 0.5
                freight_borne = "SELLER" if rng.random() < 0.7 else "BUYER"
                return_rows.append((
                    rrid, uid, oid, "APPROVED", "REFUND", D(pay_amount),
                    rng.choice(["不喜欢/不想要了", "质量问题", "尺寸不合适", "收到时破损"]),
                    "", 1 if need_return else 0, freight_borne,
                    ("顺丰" if need_return else None),
                    (str(rng.randint(100000000000, 999999999999)) if need_return else None),
                    "核验无误，同意退款",
                    fmt(return_approved) if return_approved else None,
                    fmt(return_created) if return_created else None,
                    fmt(return_completed) if return_completed else None,
                    fmt(return_created) if return_created else None))

            # 评价（已送达/已完成 的订单项）
            if lifecycle in ("COMPLETED", "SHIPPED"):
                base = completed_at if completed_at else shipped_at
                # 直接遍历本单 item 行，反查 order_item id
                start_idx = len(item_rows) - len(item_specs)
                for idx_in_order, (pid, qty, unit_price, st, p) in enumerate(item_specs):
                    oiid = item_rows[start_idx + idx_in_order][0]
                    if oiid in used_review_items:
                        continue
                    if rng.random() < 0.6:
                        r = rng.random()
                        if r < 0.7:
                            rating = rng.choice([5, 5, 4, 4, 5])
                            content = rng.choice(REVIEW_GOOD)
                        elif r < 0.9:
                            rating = 3
                            content = rng.choice(REVIEW_MID)
                        else:
                            rating = rng.choice([1, 2])
                            content = rng.choice(REVIEW_BAD)
                        rvid += 1
                        rv_created = (base + timedelta(days=rng.randint(1, 5))) if base else (created + timedelta(days=rng.randint(1,5)))
                        review_rows.append((
                            rvid, oid, oiid, pid, uid, rating, content,
                            fmt(rv_created), fmt(rv_created), None, None, None, 0))
                        used_review_items.add(oiid)

            total_orders += 1

        # 更新该用户统计
        stats["balance"] = bal
        stats["points"] = pts
        stats["total_spent"] = spent
        stats["member_level"] = tier_of(spent)

    # ---- 社交/补充数据 ----
    for uid in user_ids:
        rng = random.Random(SEED * 99 + uid)
        # 收藏（去重 product，避免 (user,product) 唯一约束）
        nfav = rng.randint(3, 10)
        fav_seen = set()
        for _ in range(nfav):
            pid = rng.choice(prod_ids)
            if pid in fav_seen:
                continue
            fav_seen.add(pid)
            favid += 1
            fav_rows.append((favid, uid, pid, prod_by_id[pid]["price"], fmt(rand_dt(rng, start_dt, end_dt)), fmt(end_dt)))
        # 购物车（部分用户）
        if rng.random() < 0.7:
            ncart = rng.randint(1, 6)
            seen = set()
            for _ in range(ncart):
                pid = rng.choice(prod_ids)
                if (uid, pid) in seen:
                    continue
                seen.add((uid, pid))
                cartid += 1
                cart_rows.append((cartid, uid, pid, rng.randint(1, 3), 1,
                                  fmt(rand_dt(rng, start_dt, end_dt)), fmt(end_dt), ""))
        # 地址
        naddr = rng.randint(1, 3)
        for ai in range(naddr):
            prov, city, dist = rng.choice(PROVINCES)
            addrid += 1
            addr_rows.append((addrid, uid, f"收货人{uid}", f"13{rng.randint(100000000,999999999)}",
                              prov, city, dist, f"{rng.choice(STREETS)}{rng.randint(1,99)}号",
                              1 if ai == 0 else 0, fmt(rand_dt(rng, start_dt, end_dt)), fmt(end_dt)))
        # 消息
        nmsg = rng.randint(1, 5)
        for _ in range(nmsg):
            t, c = rng.choice(MSG_TITLES)
            msgid += 1
            msg_rows.append((msgid, uid, "SYSTEM", t, c, None, None,
                             f"msg{uid}-{msgid}", rng.randint(0, 1),
                             fmt(rand_dt(rng, start_dt, end_dt)), fmt(end_dt)))
        # 浏览停留
        ndwell = rng.randint(5, 20)
        for _ in range(ndwell):
            pid = rng.choice(prod_ids)
            dwellid += 1
            dwell_rows.append((dwellid, uid, pid, rng.randint(3, 600),
                               rng.choice(["home", "search", "category", "recommend"]),
                               fmt(rand_dt(rng, start_dt, end_dt))))
        # 持有未用券（活人感，去重 (user,coupon)）
        if rng.random() < 0.5:
            cp = rng.choice(coupons)
            if cp["id"] in used_cp:
                pass
            else:
                used_cp.add(cp["id"])
                ucid += 1
                received = rand_dt(rng, start_dt, end_dt)
                ucoupon_rows.append((ucid, cp["id"], uid, "UNUSED", None,
                                     fmt(received), None, fmt(received), fmt(end_dt)))
                coupon_used[cp["id"]] = coupon_used.get(cp["id"], 0) + 1

    # ---- 写库 ----
    if APPLY:
        def bulk(table, cols, rows):
            if not rows:
                return
            ncol = len(cols)
            for i, r in enumerate(rows):
                if len(r) != ncol:
                    raise ValueError(f"[{table}] row #{i} has {len(r)} values, expected {ncol}: {r}")
            ph = ",".join(["%s"] * len(cols))
            colsql = ",".join(cols)
            cur.executemany(f"INSERT INTO `{table}` ({colsql}) VALUES ({ph})", rows)

        bulk("sys_user", ["id","username","password_hash","nickname","avatar_url","phone","email","gender",
                          "birthday","role","status","balance","must_change_password","points",
                          "member_level","total_spent","created_at","updated_at","deleted"], new_user_rows)
        bulk("orders", ["id","order_no","user_id","total_amount","freight_amount","discount_amount",
                        "pay_amount","status","payment_status","receiver_name","receiver_phone","receiver_address",
                        "remark","ship_company","ship_no","refund_status","refund_reason","refund_remark",
                        "refunded_at","user_coupon_id","coupon_name","paid_at","shipped_at","completed_at",
                        "canceled_at","closed_at","created_at","updated_at","activity_id","activity_discount",
                        "activity_name","member_discount","points_discount","points_used","points_earned",
                        "member_level","fulfillment_type","pickup_store_id","pickup_store_name","pickup_code",
                        "delivery_slot"], orders_rows)
        bulk("order_item", ["id","order_id","product_id","product_name","product_sku","product_cover_url",
                            "product_price","original_price","flash_sale_id","quantity","subtotal_amount","sku_spec"], item_rows)
        bulk("wallet_transaction", ["id","transaction_no","user_id","order_id","type","amount","balance_before",
                                    "balance_after","status","remark","created_at"], wallet_rows)
        bulk("recharge_order", ["id","order_no","user_id","amount","method","status","expire_at","paid_at",
                                "created_at","updated_at"], recharge_rows)
        bulk("payment_record", ["id","order_id","payment_no","channel","amount","status","paid_at","created_at",
                                "updated_at"], payment_rows)
        bulk("point_ledger", ["id","user_id","type","amount","balance_after","ref_order_id","remark","created_at"], point_rows)
        bulk("product_review", ["id","order_id","order_item_id","product_id","user_id","rating","content",
                                "created_at","updated_at","image_urls","reply_content","reply_at","hidden"], review_rows)
        bulk("return_request", ["id","user_id","order_id","status","type","refund_amount","reason","remark",
                                "need_return","freight_borne_by","express_company","tracking_no","admin_remark",
                                "approved_at","shipped_back_at","completed_at","created_at"], return_rows)
        # user_coupon 唯一键 (user_id, coupon_id)：按 (user, coupon) 去重，USED 优先，杜绝唯一键冲突
        _seen = {}
        for r in ucoupon_rows:
            key = (r[2], r[1])  # (user_id, coupon_id)
            if key not in _seen or r[3] == "USED":
                _seen[key] = r
        ucoupon_rows = list(_seen.values())
        # 去重后重新统计每张券的领取人数，保证 received_count 准确
        coupon_used = {}
        for r in ucoupon_rows:
            coupon_used[r[1]] = coupon_used.get(r[1], 0) + 1
        bulk("user_coupon", ["id","coupon_id","user_id","status","order_id","received_at","used_at","created_at",
                             "updated_at"], ucoupon_rows)
        bulk("user_favorite", ["id","user_id","product_id","price_at_favorite","created_at","updated_at"], fav_rows)
        bulk("cart_item", ["id","user_id","product_id","quantity","selected","created_at","updated_at","sku_spec"], cart_rows)
        bulk("user_address", ["id","user_id","receiver_name","receiver_phone","province","city","district",
                              "detail_address","is_default","created_at","updated_at"], addr_rows)
        bulk("user_message", ["id","user_id","type","title","content","link_view","link_ref","dedupe_key",
                              "is_read","created_at","updated_at"], msg_rows)
        bulk("page_dwell", ["id","user_id","product_id","seconds","source","created_at"], dwell_rows)

        # 回填 orders.user_coupon_id（规避与 user_coupon 的互引外键：先插订单，再插券，最后 JOIN 回填）
        cur.execute("UPDATE orders o INNER JOIN user_coupon uc ON uc.order_id=o.id AND uc.status='USED' SET o.user_coupon_id=uc.id")

        # 更新商品 stock / sales（规则⑤）
        for pid in prod_ids:
            final_stock = avail[pid]
            final_sales = prod_sales.get(pid, 0)
            cur.execute("UPDATE product SET stock=%s, sales=%s WHERE id=%s", (final_stock, final_sales, pid))
        # 更新优惠券 received_count / total_count（规则②联动）
        for cp in coupons:
            used = coupon_used.get(cp["id"], 0)
            cur.execute("SELECT total_count FROM coupon WHERE id=%s", (cp["id"],))
            tc = cur.fetchone()[0]
            new_tc = max(tc, used)
            cur.execute("UPDATE coupon SET received_count=%s, total_count=%s WHERE id=%s", (used, new_tc, cp["id"]))
        # 更新所有用户统计（规则①③⑥⑦）
        for uid in user_ids:
            s = user_stats[uid]
            cur.execute("UPDATE sys_user SET balance=%s, points=%s, total_spent=%s, member_level=%s WHERE id=%s",
                        (D(s["balance"]), s["points"], D(s["total_spent"]), s["member_level"], uid))

        conn.commit()

    # ---- 写基线 manifest（供断言用，规则⑤独立校验）----
    _gen_baseline_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "_gen_baseline.json")
    with open(_gen_baseline_path, "w", encoding="utf-8") as f:
        json.dump({"baseline": baseline,
                   "expected_stock": {str(k): avail[k] for k in prod_ids},
                   "expected_sales": {str(k): prod_sales.get(k, 0) for k in prod_ids}},
                  f, ensure_ascii=False, indent=2)

    # ---- 统计 ----
    print(f"用户数(含保留): {len(user_ids)}  新用户: {len(new_user_rows)}")
    print(f"订单: {total_orders}  订单项: {len(item_rows)}")
    print(f"钱包流水: {len(wallet_rows)}  充值单: {len(recharge_rows)}  支付记录: {len(payment_rows)}")
    print(f"积分流水: {len(point_rows)}  评价: {len(review_rows)}  退货: {len(return_rows)}")
    print(f"收藏: {len(fav_rows)}  购物车: {len(cart_rows)}  地址: {len(addr_rows)}  消息: {len(msg_rows)}  浏览: {len(dwell_rows)}")
    print(f"券领取记录: {len(ucoupon_rows)}")
    # 余额合计
    inc = sum((w[5] for w in wallet_rows if w[4] in ("RECHARGE","REFUND")), Decimal(0))
    exp = sum((w[5] for w in wallet_rows if w[4] == "PAYMENT"), Decimal(0))
    print(f"钱包合计 收入(充+退): {inc}  支出(付): {exp}  净余额: {inc-exp}")
    if APPLY:
        print("========== 已写入数据库 ==========")
    else:
        print("========== DRY RUN 完成（未写库）==========")
    conn.close()


if __name__ == "__main__":
    main()
