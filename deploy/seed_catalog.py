# -*- coding: utf-8 -*-
"""
超市系统 - 初始化商品目录 + 生成生图提示词
- 清理测试/分页测试/临时 垃圾商品与分类（保留 4 真实商品 + 4 真实分类）
- 新增 2 个分类（粮油调味 / 乳品烘焙）+ 26 个商品，封面图 URL 全部留空
- 输出 deploy/seed-products-prompts.md（每个商品的 AI 生图提示词 + 建议文件名）
运行: python deploy/seed_catalog.py
"""
import pymysql

HOST, USER, PASS, DB = "127.0.0.1", "root", "zzmsgz", "supermarket_system"
KEEP_PRODUCT_IDS = (1, 2, 3, 9)          # 红富士苹果 / 矿泉水 / 原味薯片 / 芒果
KEEP_CATEGORY_IDS = (1, 2, 3, 4)         # 生鲜食品 / 酒水饮料 / 休闲零食 / 日用百货

# ---------------- 分类定义（已存在则复用 id，否则新增）----------------
CATEGORIES = [
    {"key": "fresh",  "name": "生鲜食品", "sort_no": 10},
    {"key": "drink",  "name": "酒水饮料", "sort_no": 20},
    {"key": "snack",  "name": "休闲零食", "sort_no": 30},
    {"key": "daily",  "name": "日用百货", "sort_no": 40},
    {"key": "staple", "name": "粮油调味", "sort_no": 50},
    {"key": "dairy",  "name": "乳品烘焙", "sort_no": 60},
]

# ---------------- 商品目录（30 个）----------------
# existing_id 非空=更新该真实商品（不改 price/stock）；否则新增
PRODUCTS = [
    # 生鲜食品
    dict(cat="fresh", sku="FRESH-APPLE-001", name="红富士苹果", existing_id=1,
         subtitle="脆甜多汁 产地直发", price=12.80, unit="斤", brand="烟台",
         stock=90, low=10, sales=1820, hot=1, new=0, tags="水果,新鲜,甜",
         desc="精选山东烟台红富士苹果，皮薄肉脆、清甜多汁，适合鲜食与拼盘。",
         prompt="Professional e-commerce product photo of red Fuji apples, vibrant red and yellow skin with natural shine, a few water droplets, arranged neatly on a clean white background, soft studio lighting, high resolution, commercial food photography"),
    dict(cat="fresh", sku="FRESH-MANGO-001", name="芒果", existing_id=9,
         subtitle="热带香甜 肉厚核小", price=20.00, unit="斤", brand="海南",
         stock=99, low=10, sales=960, hot=0, new=0, tags="热带,水果,甜",
         desc="海南产小台农芒果，果肉金黄、香气浓郁，甜而不腻。",
         prompt="Professional product photo of ripe yellow mangoes, smooth golden skin, one sliced showing orange flesh, on a clean white background, soft natural light, high resolution, e-commerce fruit photography"),
    dict(cat="fresh", sku="FRESH-BANANA-001", name="香蕉",
         subtitle="软糯香甜 自然成熟", price=6.90, unit="斤", brand="菲律宾",
         stock=120, low=10, sales=740, hot=0, new=1, tags="水果,粗粮,健康",
         desc="进口香蕉自然催熟，口感软糯、富含钾元素，老人小孩都爱。",
         prompt="Professional product photo of a bunch of ripe yellow bananas with subtle brown tips, on a clean white background, soft studio light, high resolution, commercial fruit photography"),
    dict(cat="fresh", sku="FRESH-TOMATO-001", name="西红柿",
         subtitle="沙瓤多汁 凉拌炒菜", price=5.50, unit="斤", brand="自然熟",
         stock=150, low=15, sales=610, hot=0, new=0, tags="蔬菜,生鲜,沙拉",
         desc="自然熟西红柿，皮薄沙瓤、酸甜适口，生吃凉拌或炒菜皆宜。",
         prompt="Professional product photo of fresh red tomatoes with green stems, glossy skin and water droplets, on a clean white background, studio lighting, high resolution, e-commerce vegetable photography"),
    dict(cat="fresh", sku="FRESH-EGG-001", name="鸡蛋",
         subtitle="新鲜土鸡蛋 营养早餐", price=15.90, unit="盒", brand="正大",
         stock=80, low=10, sales=520, hot=0, new=0, tags="禽蛋,营养,早餐",
         desc="新鲜农场鸡蛋，蛋黄饱满、蛋白嫩滑，家庭日常必备。",
         prompt="Professional product photo of a carton of fresh brown eggs, one egg cracked showing a bright yolk, on a clean white background, soft light, high resolution, commercial food photography"),

    # 酒水饮料
    dict(cat="drink", sku="DRINK-WATER-001", name="矿泉水", existing_id=2,
         subtitle="天然弱碱 解渴畅饮", price=2.50, unit="瓶", brand="农夫山泉",
         stock=169, low=20, sales=2310, hot=1, new=0, tags="饮用水,解渴,瓶装",
         desc="天然水源弱碱性矿泉水，口感清冽，运动出行随身带。",
         prompt="Professional product photo of a clear plastic bottle of mineral water with a blue label, condensation droplets on the surface, on a clean white background, studio lighting, high resolution, commercial beverage photography"),
    dict(cat="drink", sku="DRINK-COLA-001", name="可乐",
         subtitle="经典碳酸 冰爽畅快", price=3.50, unit="瓶", brand="可口可乐",
         stock=200, low=20, sales=1560, hot=1, new=0, tags="碳酸饮料,冰爽",
         desc="经典红罐可乐，气泡充足、冰镇更爽口，聚餐必备。",
         prompt="Professional product photo of a red cola beverage can with condensation droplets, on a clean white background, studio lighting, high resolution, commercial drink photography"),
    dict(cat="drink", sku="DRINK-ORANGE-001", name="鲜橙汁",
         subtitle="真实果肉 维C满满", price=8.90, unit="瓶", brand="美汁源",
         stock=110, low=15, sales=430, hot=0, new=1, tags="果汁,维C,早餐",
         desc="含真实果肉的鲜橙汁，酸甜可口，补充每日维C。",
         prompt="Professional product photo of a bottle of orange juice with visible pulp, bright orange liquid, on a clean white background, soft light, high resolution, e-commerce beverage photography"),
    dict(cat="drink", sku="DRINK-GREEN-TEA-001", name="绿茶",
         subtitle="无糖零卡 清爽解腻", price=4.50, unit="瓶", brand="农夫山泉",
         stock=130, low=15, sales=380, hot=0, new=0, tags="茶饮,无糖,零卡",
         desc="0 糖 0 卡绿茶饮料，茶香清爽、解腻去油。",
         prompt="Professional product photo of a green tea bottle with pale tea color and a minimalist label, on a clean white background, studio lighting, high resolution, commercial drink photography"),
    dict(cat="drink", sku="DRINK-BEER-001", name="啤酒",
         subtitle="麦芽酿造 醇厚泡沫", price=6.00, unit="瓶", brand="青岛",
         stock=160, low=15, sales=690, hot=0, new=0, tags="酒类,麦芽,聚餐",
         desc="经典麦芽啤酒，泡沫细腻、口感醇厚，宵夜聚餐好搭档。",
         prompt="Professional product photo of a brown beer bottle with a foamy head and golden liquid, on a clean white background, studio light, high resolution, e-commerce alcohol photography"),

    # 休闲零食
    dict(cat="snack", sku="SNACK-CHIPS-001", name="原味薯片", existing_id=3,
         subtitle="酥脆可口 追剧必备", price=6.90, unit="袋", brand="乐事",
         stock=336, low=20, sales=1990, hot=1, new=0, tags="零食,膨化,追剧",
         desc="原味薯片，薄脆酥香、口口上瘾，办公室追剧小零嘴。",
         prompt="Professional product photo of a bag of original flavor potato chips with crisp golden chips spilling out, on a clean white background, bright studio lighting, high resolution, e-commerce snack photography"),
    dict(cat="snack", sku="SNACK-CHOCO-001", name="巧克力",
         subtitle="丝滑牛奶 甜而不腻", price=12.90, unit="盒", brand="德芙",
         stock=90, low=10, sales=870, hot=1, new=0, tags="甜食,巧克力,礼物",
         desc="丝滑牛奶巧克力，入口即化、甜度适中，下午茶小确幸。",
         prompt="Professional product photo of a box of milk chocolate bars with a glossy brown surface, on a clean white background, soft light, high resolution, commercial snack photography"),
    dict(cat="snack", sku="SNACK-SEEDS-001", name="瓜子",
         subtitle="香脆炒货 闲聊零食", price=7.50, unit="袋", brand="洽洽",
         stock=140, low=15, sales=640, hot=0, new=0, tags="炒货,休闲,咸香",
         desc="香脆瓜子，颗粒饱满、咸香入味，看球闲聊好伴侣。",
         prompt="Professional product photo of a bag of roasted sunflower seeds with a small pile spilled out, on a clean white background, studio lighting, high resolution, e-commerce snack photography"),
    dict(cat="snack", sku="SNACK-COOKIE-001", name="饼干",
         subtitle="夹心酥脆 老少皆宜", price=9.90, unit="盒", brand="奥利奥",
         stock=100, low=10, sales=720, hot=0, new=1, tags="烘焙,零食,夹心",
         desc="巧克力夹心饼干，一口酥脆、甜香浓郁，孩子大人都喜欢。",
         prompt="Professional product photo of a package of chocolate sandwich cookies with a few cookies separated, on a clean white background, bright light, high resolution, commercial biscuit photography"),
    dict(cat="snack", sku="SNACK-LATIAO-001", name="辣条",
         subtitle="麻辣过瘾 童年味道", price=3.90, unit="袋", brand="卫龙",
         stock=180, low=20, sales=1120, hot=0, new=0, tags="辣味,小吃,怀旧",
         desc="经典辣条，麻辣鲜香、越嚼越上瘾，解馋小零食。",
         prompt="Professional product photo of a package of spicy gluten snacks (latiao) with reddish oily strips, on a clean white background, studio lighting, high resolution, e-commerce snack photography"),

    # 日用百货
    dict(cat="daily", sku="DAILY-TISSUE-001", name="抽纸",
         subtitle="柔软亲肤 居家必备", price=19.90, unit="提", brand="心相印",
         stock=200, low=20, sales=980, hot=1, new=0, tags="纸品,家用,柔软",
         desc="原生木浆抽纸，柔软不掉屑，家庭日常用纸首选。",
         prompt="Professional product photo of soft tissue paper packs stacked neatly, clean packaging, on a clean white background, soft light, high resolution, e-commerce household photography"),
    dict(cat="daily", sku="DAILY-LAUNDRY-001", name="洗衣液",
         subtitle="深层去污 温和不伤手", price=29.90, unit="瓶", brand="蓝月亮",
         stock=120, low=15, sales=560, hot=0, new=0, tags="清洁,洗护,衣物",
         desc="高效洗衣液，深层去污、易漂洗，呵护衣物与双手。",
         prompt="Professional product photo of a laundry detergent bottle with blue liquid, on a clean white background, studio lighting, high resolution, commercial cleaning product photography"),
    dict(cat="daily", sku="DAILY-TOOTHPASTE-001", name="牙膏",
         subtitle="清新口气 呵护牙龈", price=12.90, unit="支", brand="高露洁",
         stock=150, low=15, sales=430, hot=0, new=0, tags="口腔护理,清新",
         desc="含氟防蛀牙膏，清新薄荷味、呵护牙龈健康。",
         prompt="Professional product photo of a toothpaste tube with a mint theme, on a clean white background, soft light, high resolution, e-commerce personal care photography"),
    dict(cat="daily", sku="DAILY-TRASHBAG-001", name="垃圾袋",
         subtitle="加厚结实 不易破漏", price=9.90, unit="卷", brand="美丽雅",
         stock=220, low=20, sales=510, hot=0, new=0, tags="日用,清洁,加厚",
         desc="加厚垃圾袋，承重力强、不易破裂，厨房卫生间通用。",
         prompt="Professional product photo of rolled garbage bags with a few separated, on a clean white background, studio lighting, high resolution, commercial household photography"),
    dict(cat="daily", sku="DAILY-HANGER-001", name="衣架",
         subtitle="防滑耐用 衣柜整理", price=14.90, unit="个", brand="无印",
         stock=130, low=15, sales=360, hot=0, new=1, tags="收纳,家居,防滑",
         desc="PP 材质防滑衣架，轻巧耐用，整齐收纳各类衣物。",
         prompt="Professional product photo of plastic clothes hangers grouped together, on a clean white background, soft light, high resolution, e-commerce home photography"),

    # 粮油调味
    dict(cat="staple", sku="GROC-RICE-001", name="大米",
         subtitle="颗粒饱满 清香软糯", price=39.90, unit="袋", brand="五常",
         stock=90, low=10, sales=820, hot=1, new=0, tags="主食,米,三餐",
         desc="东北五常稻花香大米，粒粒饱满、蒸饭清香软糯。",
         prompt="Professional product photo of a bag of white rice with a small pile of grains spilled beside it, on a clean white background, studio lighting, high resolution, commercial staple food photography"),
    dict(cat="staple", sku="GROC-OIL-001", name="食用油",
         subtitle="非转基因 清淡少油烟", price=59.90, unit="桶", brand="金龙鱼",
         stock=70, low=10, sales=470, hot=0, new=0, tags="食用油,烹饪,健康",
         desc="非转基因调和油，清淡少油烟，煎炒烹炸都合适。",
         prompt="Professional product photo of a bottle of golden cooking oil, on a clean white background, soft light, high resolution, e-commerce food photography"),
    dict(cat="staple", sku="GROC-SOY-001", name="生抽",
         subtitle="酿造酱油 提鲜上色", price=8.90, unit="瓶", brand="海天",
         stock=160, low=15, sales=610, hot=0, new=0, tags="调味,酱油,烹饪",
         desc="传统酿造生抽，鲜咸适口、上色自然，凉拌炒菜皆宜。",
         prompt="Professional product photo of a soy sauce bottle with dark liquid, on a clean white background, studio lighting, high resolution, commercial condiment photography"),
    dict(cat="staple", sku="GROC-SALT-001", name="食盐",
         subtitle="精制加碘 日常调味", price=2.90, unit="袋", brand="中盐",
         stock=300, low=30, sales=530, hot=0, new=0, tags="调味,盐,必备",
         desc="精制加碘食盐，颗粒细腻、溶解快，家常调味必备。",
         prompt="Professional product photo of a salt package with white grains spilled out, on a clean white background, soft light, high resolution, e-commerce condiment photography"),
    dict(cat="staple", sku="GROC-VINEGAR-001", name="醋",
         subtitle="粮食酿造 酸香开胃", price=6.90, unit="瓶", brand="恒顺",
         stock=140, low=15, sales=400, hot=0, new=0, tags="调味,醋,凉拌",
         desc="粮食酿造食醋，酸香醇厚、凉拌蘸食都开胃。",
         prompt="Professional product photo of a vinegar bottle with dark brown liquid, on a clean white background, studio lighting, high resolution, commercial condiment photography"),

    # 乳品烘焙
    dict(cat="dairy", sku="DAIRY-MILK-001", name="纯牛奶",
         subtitle="生牛乳 营养早餐", price=19.90, unit="盒", brand="蒙牛",
         stock=180, low=20, sales=1340, hot=1, new=0, tags="乳制品,早餐,补钙",
         desc="100% 生牛乳纯牛奶，富含蛋白与钙，早餐温饮皆宜。",
         prompt="Professional product photo of a carton of pure milk with white and blue packaging, on a clean white background, soft light, high resolution, e-commerce dairy photography"),
    dict(cat="dairy", sku="DAIRY-YOGURT-001", name="酸奶",
         subtitle="浓稠畅轻 活性菌", price=15.90, unit="杯", brand="安慕希",
         stock=150, low=15, sales=770, hot=0, new=0, tags="乳制品,酸奶,益生菌",
         desc="希腊风味酸奶，浓稠醇厚、富含活性菌，饭后小食。",
         prompt="Professional product photo of yogurt cups with creamy white texture, on a clean white background, studio lighting, high resolution, commercial dairy photography"),
    dict(cat="dairy", sku="DAIRY-BREAD-001", name="面包",
         subtitle="松软吐司 即食早餐", price=8.90, unit="个", brand="桃李",
         stock=120, low=15, sales=690, hot=0, new=1, tags="烘焙,早餐,吐司",
         desc="松软吐司面包，麦香浓郁、口感绵软，匆忙早晨随手拿。",
         prompt="Professional product photo of a sliced bread loaf with golden crust, on a clean white background, soft light, high resolution, e-commerce bakery photography"),
    dict(cat="dairy", sku="DAIRY-CAKE-001", name="蛋糕",
         subtitle="鲜奶油 节日甜点", price=25.90, unit="盒", brand="好利来",
         stock=60, low=10, sales=350, hot=0, new=0, tags="烘焙,甜点,庆祝",
         desc="鲜奶油水果蛋糕，绵软香甜、造型精致，生日聚会首选。",
         prompt="Professional product photo of a whole cream cake decorated with fresh strawberries, on a clean white background, studio lighting, high resolution, commercial dessert photography"),
    dict(cat="dairy", sku="DAIRY-BUTTER-001", name="黄油",
         subtitle="动物奶油 烘焙原料", price=22.90, unit="盒", brand="安佳",
         stock=90, low=10, sales=300, hot=0, new=0, tags="乳制品,烘焙,涂抹",
         desc="动物性黄油，奶香浓郁、易涂抹，烘焙与煎烤好帮手。",
         prompt="Professional product photo of a block of butter with a knife spread showing golden yellow texture, on a clean white background, soft light, high resolution, e-commerce dairy photography"),
]


def main():
    conn = pymysql.connect(host=HOST, user=USER, password=PASS, database=DB,
                           charset="utf8mb4", autocommit=False)
    cur = conn.cursor()
    try:
        # ---------- 清理测试垃圾 ----------
        # 订单与 user_coupon 存在双向外键，临时关闭约束以安全删除完整子集
        cur.execute("SET FOREIGN_KEY_CHECKS=0")
        cur.execute("SELECT id FROM product WHERE id NOT IN %s", (KEEP_PRODUCT_IDS,))
        junk_pids = [r[0] for r in cur.fetchall()]
        cur.execute("SELECT id FROM product_category WHERE id NOT IN %s", (KEEP_CATEGORY_IDS,))
        junk_cids = [r[0] for r in cur.fetchall()]

        # 仅含垃圾商品的订单（其所有 order_item 都是垃圾商品）
        cur.execute(
            "SELECT o.id FROM orders o WHERE NOT EXISTS ("
            "SELECT 1 FROM order_item oi WHERE oi.order_id=o.id "
            "AND oi.product_id NOT IN %s)", (KEEP_PRODUCT_IDS,))
        junk_oids = [r[0] for r in cur.fetchall()]

        print(f"[清理] 垃圾商品 {len(junk_pids)} 个, 垃圾分类 {len(junk_cids)} 个, 纯垃圾订单 {len(junk_oids)} 个")

        # 子表（按 product）
        for tbl, col in [("cart_item", "product_id"), ("page_dwell", "product_id"),
                         ("product_attribute", "product_id"), ("product_image", "product_id"),
                         ("product_sku", "product_id"), ("product_review", "product_id"),
                         ("stock_log", "product_id"), ("order_item", "product_id")]:
            if junk_pids:
                cur.execute(f"DELETE FROM {tbl} WHERE {col} IN %s", (junk_pids,))
        # 子表（按 order）
        for tbl in ["payment_record", "product_review", "stock_log",
                    "user_coupon", "wallet_transaction", "order_item"]:
            if junk_oids:
                cur.execute(f"DELETE FROM {tbl} WHERE order_id IN %s", (junk_oids,))
        if junk_oids:
            cur.execute("DELETE FROM orders WHERE id IN %s", (junk_oids,))
        if junk_pids:
            cur.execute("DELETE FROM product WHERE id IN %s", (junk_pids,))
        if junk_cids:
            cur.execute("DELETE FROM product_category WHERE id IN %s", (junk_cids,))
        cur.execute("SET FOREIGN_KEY_CHECKS=1")
        conn.commit()
        print("[清理] 垃圾数据已删除")

        # ---------- 确保分类存在，取 id 映射 ----------
        cat_id = {}
        for c in CATEGORIES:
            cur.execute("SELECT id FROM product_category WHERE name=%s", (c["name"],))
            row = cur.fetchone()
            if row:
                cat_id[c["key"]] = row[0]
            else:
                cur.execute(
                    "INSERT INTO product_category (parent_id, name, sort_no, status, created_at, updated_at, deleted) "
                    "VALUES (0, %s, %s, 1, NOW(), NOW(), 0)", (c["name"], c["sort_no"]))
                cat_id[c["key"]] = cur.lastrowid
        conn.commit()
        print(f"[分类] 现有分类 id 映射: {cat_id}")

        # ---------- 写入商品 ----------
        n_insert = n_update = 0
        for i, p in enumerate(PRODUCTS, 1):
            cid = cat_id[p["cat"]]
            op = round(p["price"] * 1.25, 2) if p.get("hot") else None
            if p.get("existing_id"):
                # 更新真实商品（保留原 price/stock）
                cur.execute(
                    """UPDATE product SET category_id=%s, sku=%s, name=%s, subtitle=%s,
                       description=%s, original_price=%s, low_stock_threshold=%s, sales=%s,
                       unit=%s, status='ON_SALE', brand=%s, is_hot=%s, is_new=%s,
                       sort_no=%s, tags=%s, cover_url=NULL, updated_at=NOW()
                       WHERE id=%s""",
                    (cid, p["sku"], p["name"], p["subtitle"], p["desc"], op,
                     p["low"], p["sales"], p["unit"], p["brand"], p["hot"], p["new"],
                     i, p["tags"], p["existing_id"]))
                n_update += 1
            else:
                cur.execute(
                    """INSERT INTO product
                       (category_id, sku, name, subtitle, description, cover_url, price,
                        original_price, stock, low_stock_threshold, sales, unit, status,
                        brand, is_hot, is_new, sort_no, tags, created_at, updated_at, deleted)
                       VALUES (%s,%s,%s,%s,%s,NULL,%s,%s,%s,%s,%s,%s,'ON_SALE',%s,%s,%s,%s,%s,NOW(),NOW(),0)""",
                    (cid, p["sku"], p["name"], p["subtitle"], p["desc"], p["price"], op,
                     p["stock"], p["low"], p["sales"], p["unit"], p["brand"], p["hot"],
                     p["new"], i, p["tags"]))
                n_insert += 1
        conn.commit()
        print(f"[商品] 新增 {n_insert} 个, 更新 {n_update} 个")

        # ---------- 输出提示词文档 ----------
        write_prompts_md(cat_id)

        # ---------- 校验 ----------
        cur.execute("SELECT COUNT(*) FROM product_category")
        print(f"[校验] 分类总数 = {cur.fetchone()[0]}")
        cur.execute("SELECT COUNT(*) FROM product WHERE deleted=0")
        print(f"[校验] 商品总数(未删除) = {cur.fetchone()[0]}")
        cur.execute("SELECT COUNT(*) FROM product WHERE cover_url IS NULL OR cover_url=''")
        print(f"[校验] 封面图为空的商品的 = {cur.fetchone()[0]}")
    finally:
        conn.close()


def write_prompts_md(cat_id):
    id2name = {
        "fresh": "生鲜食品", "drink": "酒水饮料", "snack": "休闲零食",
        "daily": "日用百货", "staple": "粮油调味", "dairy": "乳品烘焙"}
    lines = ["# 商品图片生图提示词（共 %d 个）\n" % len(PRODUCTS)]
    lines.append("> 用法：用下列英文提示词在任意 AI 绘图工具（Midjourney / DALL·E / 通义万相 / Stable Diffusion 等）生成商品图，\n"
                 "> 保存为**建议文件名**，然后在后台「商品管理 → 编辑」里上传该图即可（封面图 URL 已为你留空）。\n")
    lines.append("| 序号 | 商品名 | SKU | 分类 | 建议文件名 | 生图提示词(英文) | 中文说明 |")
    lines.append("| --- | --- | --- | --- | --- | --- | --- |")
    for i, p in enumerate(PRODUCTS, 1):
        fname = p["sku"].lower() + ".png"
        catname = id2name.get(p["cat"], p["cat"])
        zh = p["desc"][:18]
        # 表格内换行用 <br>
        prompt_cell = p["prompt"].replace("|", "/")
        lines.append(f"| {i} | {p['name']} | {p['sku']} | {catname} | `{fname}` | {prompt_cell} | {zh} |")
    with open("deploy/seed-products-prompts.md", "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print("[文档] 已生成 deploy/seed-products-prompts.md")


if __name__ == "__main__":
    main()
