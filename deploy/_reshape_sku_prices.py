"""重塑规格价：给 31 个商品、70 条 product_sku 设定阶梯规格价。

- 直接 UPDATE 运行库（id 1..70 显式 CASE，幂等）。
- 改写 backend/.../db/seed-data.sql 的 product_sku 插入块，加 price 列，
  保证「全新库」也能拿到规格价（存量库靠 INSERT IGNORE 跳过，不覆盖后台改过的价）。

商品基准价 = 最小规格价（已核对与 product.price 一致）；更大规格按容量/数量阶梯加价，
且「大包装每单位更便宜」的直觉成立。
"""
import sys
import re
import pathlib

sys.path.insert(0, "deploy")
from _db import run_sql

# sku_id -> 规格价（单位：元）。最小规格 = 商品基准价，不另设价时也等于 base。
PRICES = {
    1: 12.80, 2: 23.80, 3: 2.50, 4: 3.50, 5: 5.90,
    6: 6.90, 7: 11.90, 8: 18.90, 9: 15.90, 10: 29.90,
    11: 3.50, 12: 4.90, 13: 9.90, 14: 39.90, 15: 75.90,
    16: 19.90, 17: 36.90, 18: 89.00, 19: 128.00, 20: 188.00,
    21: 59.90, 22: 110.00, 23: 22.80, 24: 42.80, 25: 68.00,
    26: 128.00, 27: 89.00, 28: 205.00, 29: 399.00, 30: 19.90,
    31: 36.90, 32: 71.90, 33: 25.90, 34: 47.90, 35: 45.90,
    36: 79.90, 37: 39.90, 38: 75.90, 39: 59.90, 40: 109.00,
    41: 39.90, 42: 159.00, 43: 45.00, 44: 65.00, 45: 85.00,
    46: 89.90, 47: 225.00, 48: 49.90, 49: 91.90, 50: 134.90,
    51: 12.90, 52: 23.90, 53: 9.90, 54: 14.90, 55: 15.90,
    56: 30.90, 57: 12.90, 58: 23.90, 59: 29.90, 60: 55.00,
    61: 19.90, 62: 37.90, 63: 12.90, 64: 23.90, 65: 18.90,
    66: 34.90, 67: 32.90, 68: 60.90, 69: 39.90, 70: 95.90,
}

# 硬断言：必须正好 70 条且 id 连续 1..70
assert len(PRICES) == 70, f"PRICES 数量异常: {len(PRICES)}"
assert set(PRICES) == set(range(1, 71)), "PRICES 的 id 必须连续 1..70"

seed = pathlib.Path("backend/src/main/resources/db/seed-data.sql")
text = seed.read_text(encoding="utf-8")

m = re.search(r"INSERT IGNORE INTO product_sku \(([^)]+)\) VALUES", text)
assert m, "找不到 product_sku 插入语句"

# 解析现有每一行：(id, product_id, spec_json, sku_code, image, sort_no, deleted)
row_re = re.compile(
    r"\(\s*(\d+)\s*,\s*(\d+)\s*,\s*('(?:[^']|'')*')\s*,\s*('(?:[^']|'')*')\s*,\s*(NULL|'[^']*')\s*,\s*(\d+)\s*,\s*(\d+)\s*\)"
)
end = text.rindex(";")
valblock = text[m.end():end]
rows = row_re.findall(valblock)
assert len(rows) == 70, f"解析到 {len(rows)} 行，期望 70"

# 重建带 price 的插入块（price 放在 image 之后、sort_no 之前）
new_lines = ["INSERT IGNORE INTO product_sku (id, product_id, spec_json, sku_code, image, price, sort_no, deleted) VALUES"]
for (sid, pid, spec, code, image, sortno, deleted) in rows:
    sid_i = int(sid)
    assert sid_i in PRICES, f"id={sid_i} 没有对应价格"
    price = f"{PRICES[sid_i]:.2f}"
    new_lines.append(f"  ({sid},{pid},{spec},{code},{image},{price},{sortno},{deleted}),")
block = "\n".join(new_lines)
block = block.rstrip(",") + ";"

new_text = text[:m.start()] + block + text[end + 1:]
assert new_text.count("INSERT IGNORE INTO product_sku") == 1
seed.write_text(new_text, encoding="utf-8")
print("[seed] product_sku 插入块已重写（含 price 列）")

# 直接 UPDATE 运行库
case = "CASE id " + " ".join(f"WHEN {k} THEN {v:.2f}" for k, v in PRICES.items()) + " ELSE price END"
sql = f"UPDATE product_sku SET price = {case} WHERE id BETWEEN 1 AND 70;"
out = run_sql(sql)
print("[db] update 结果:", out.strip() or "(空=成功)")

# 对账：抽查若干 + 统计非空价数量
check = run_sql(
    "SELECT COUNT(*) FROM product_sku WHERE id BETWEEN 1 AND 70 AND price IS NOT NULL;"
)
print("[db] 已设价的 sku 行数:", check.strip())
sample = run_sql(
    "SELECT id, product_id, price FROM product_sku WHERE id IN (1,5,13,20,70) ORDER BY id;"
)
print("[db] 抽样:", sample.strip())
