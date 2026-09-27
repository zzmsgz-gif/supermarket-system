"""重塑规格吊牌价(original_price)：给 product_sku 增加「规格原价」列，并按倍数推导。

推导公式：sku.original_price = sku.price × (product.original_price / product.price)
- 仅当 商品原价 > 商品基准价（即该商品本身有折扣）时生效；
- 否则（商品无折扣 / 规格价为空）→ sku.original_price = NULL（详情页回退商品级划线价，不强行划线）。
- 最小规格（=商品基准价）的吊牌价恒等于商品原价，保证与现状一致、不回退。

同时：
- ALTER 运行库加列（幂等 IF NOT EXISTS）。
- 重写 backend/.../db/seed-data.sql 的 product_sku 插入块，加 original_price 列，
  保证「全新库」也能拿到规格吊牌价（存量库靠 INSERT IGNORE 跳过，不覆盖后台改过的价）。
"""
import sys
import re
import pathlib
from decimal import Decimal, ROUND_HALF_UP

sys.path.insert(0, "deploy")
from _db import run_sql

# ---- 1. 加列（幂等） ----
run_sql(
    "ALTER TABLE product_sku "
    "ADD COLUMN original_price DECIMAL(10,2) DEFAULT NULL "
    "COMMENT '规格吊牌价（划线价）；NULL=跟随商品原价' AFTER price;"
)
print("[db] ALTER product_sku 加 original_price 列（幂等）")

# ---- 2. 取商品 price / original_price ----
prod_rows = run_sql(
    "SELECT id, price, original_price FROM product WHERE id IN ("
    "SELECT DISTINCT product_id FROM product_sku WHERE id BETWEEN 1 AND 70);"
)
products = {}
for line in prod_rows.strip().splitlines():
    if not line.strip():
        continue
    pid, price, orig = line.split("\t")
    products[int(pid)] = (Decimal(price), (Decimal(orig) if orig not in ("", "NULL") else None))

# ---- 3. 取 70 条种子 SKU ----
sku_rows = run_sql("SELECT id, product_id, price FROM product_sku WHERE id BETWEEN 1 AND 70 ORDER BY id;")
skus = []
for line in sku_rows.strip().splitlines():
    if not line.strip():
        continue
    sid, pid, price = line.split("\t")
    skus.append((int(sid), int(pid), (Decimal(price) if price not in ("", "NULL") else None)))

# ---- 4. 计算每条约束价 ----
def derive(sku_price, prod_price, prod_orig):
    if sku_price is None or prod_orig is None or prod_price in (None, Decimal(0)):
        return None
    if prod_orig <= prod_price:
        return None
    return (sku_price * (prod_orig / prod_price)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)

values = {}
for sid, pid, sku_price in skus:
    prod_price, prod_orig = products.get(pid, (None, None))
    values[sid] = derive(sku_price, prod_price, prod_orig)

# 硬断言：正好 70 条、id 连续 1..70
assert len(values) == 70, f"SKU 数量异常: {len(values)}"
assert set(values) == set(range(1, 71)), "SKU 的 id 必须连续 1..70"

# ---- 5. UPDATE 运行库 ----
when = " ".join(
    f"WHEN {k} THEN {('NULL' if v is None else format(v, 'f'))}" for k, v in values.items()
)
sql = f"UPDATE product_sku SET original_price = CASE id {when} ELSE original_price END WHERE id BETWEEN 1 AND 70;"
out = run_sql(sql)
print("[db] UPDATE 结果:", out.strip() or "(空=成功)")

# ---- 6. 重写 seed-data.sql 插入块 ----
seed = pathlib.Path("backend/src/main/resources/db/seed-data.sql")
text = seed.read_text(encoding="utf-8")
m = re.search(r"INSERT IGNORE INTO product_sku \(([^)]+)\) VALUES", text)
assert m, "找不到 product_sku 插入语句"

row_re = re.compile(
    r"\(\s*(\d+)\s*,\s*(\d+)\s*,\s*('(?:[^']|'')*')\s*,\s*('(?:[^']|'')*')\s*,\s*(NULL|'[^']*')\s*,\s*([\d.]+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)"
)
end = text.rindex(";")
valblock = text[m.end():end]
parsed = row_re.findall(valblock)
assert len(parsed) == 70, f"解析到 {len(parsed)} 行，期望 70"

new_lines = ["INSERT IGNORE INTO product_sku (id, product_id, spec_json, sku_code, image, price, original_price, sort_no, deleted) VALUES"]
for (sid, pid, spec, code, image, price, sortno, deleted) in parsed:
    sid_i = int(sid)
    assert sid_i in values, f"id={sid_i} 没有对应吊牌价"
    ov = values[sid_i]
    ov_sql = "NULL" if ov is None else format(ov, "f")
    new_lines.append(f"  ({sid},{pid},{spec},{code},{image},{price},{ov_sql},{sortno},{deleted}),")
block = "\n".join(new_lines)
block = block.rstrip(",") + ";"

new_text = text[:m.start()] + block + text[end + 1:]
assert new_text.count("INSERT IGNORE INTO product_sku") == 1
seed.write_text(new_text, encoding="utf-8")
print("[seed] product_sku 插入块已重写（含 original_price 列）")

# ---- 7. 对账 ----
total = run_sql(
    "SELECT COUNT(*) FROM product_sku WHERE id BETWEEN 1 AND 70 AND original_price IS NOT NULL;"
)
print("[db] 已设规格吊牌价的 sku 行数:", total.strip())
# 矿泉水 3/4/5 应为 3.12 / 4.37 / 7.36
sample = run_sql(
    "SELECT id, original_price FROM product_sku WHERE id IN (3,4,5) ORDER BY id;"
)
print("[db] 矿泉水抽样(id, original_price):", sample.strip().replace("\n", " | "))
# 无折扣商品（如鸡蛋 9/10）应为 NULL
null_sample = run_sql(
    "SELECT id, original_price FROM product_sku WHERE id IN (9,10) ORDER BY id;"
)
print("[db] 无折扣抽样(id, original_price):", null_sample.strip().replace("\n", " | "))
