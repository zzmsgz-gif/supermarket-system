"""种子商品图转 WebP：frontend/public/seed-products/*.jpg -> .webp（长边 800/q80/LANCZOS）。
原 jpg 移到项目根 _seed_jpg_backup/（不删除，保留回滚，且移出 public/ 不进 dist）。
同步改写 db/seed-data.sql 里 /seed-products/*.jpg 引用为 .webp（幂等）。
"""
import os, re, shutil
from PIL import Image

ROOT = r"D:/supermarket system"
SRC = os.path.join(ROOT, "frontend/public/seed-products")
BACKUP = os.path.join(ROOT, "_seed_jpg_backup")
SEED = os.path.join(ROOT, "backend/src/main/resources/db/seed-data.sql")
os.makedirs(BACKUP, exist_ok=True)

total_old = total_new = 0
conv = skip = 0
for f in sorted(os.listdir(SRC)):
    if not f.lower().endswith((".jpg", ".jpeg")):
        continue
    p = os.path.join(SRC, f)
    out = os.path.splitext(p)[0] + ".webp"
    if not os.path.exists(out):
        old = os.path.getsize(p)
        im = Image.open(p).convert("RGB")
        w, h = im.size
        scale = min(1.0, 800.0 / max(w, h))
        if scale < 1.0:
            im = im.resize((int(w * scale + 0.5), int(h * scale + 0.5)), Image.LANCZOS)
        im.save(out, "WEBP", quality=80)
        total_old += old
        total_new += os.path.getsize(out)
        conv += 1
    else:
        skip += 1
    # 原 jpg 移出 public/（rename，不删除）
    dst = os.path.join(BACKUP, f)
    if os.path.abspath(p) != os.path.abspath(dst):
        shutil.move(p, dst)
print(f"[img] 新转 {conv} 张（跳过已存在 {skip} 张）；原 jpg 已移至 {BACKUP}")
if conv:
    print(f"      本次转图体积 {total_old/1024:.1f}KB -> {total_new/1024:.1f}KB")

with open(SEED, "r", encoding="utf-8") as fh:
    txt = fh.read()
pat = re.compile(r"/seed-products/([A-Za-z0-9_\-]+)\.jpg")
new_txt, n = pat.subn(lambda m: f"/seed-products/{m.group(1)}.webp", txt)
if n:
    with open(SEED, "w", encoding="utf-8") as fh:
        fh.write(new_txt)
    print(f"[sql] seed-data.sql 替换 {n} 处 /seed-products/*.jpg -> .webp")
else:
    print("[sql] seed-data.sql 已是 .webp，跳过")
print("[ok] 静态文件与种子 SQL 已更新；线上存量库请另跑：")
print("  UPDATE product SET cover_url=REPLACE(cover_url,'.jpg','.webp') "
      "WHERE cover_url LIKE '/seed-products/%' AND cover_url LIKE '%.jpg';")
print("  UPDATE product_category SET icon_url=REPLACE(icon_url,'.jpg','.webp') "
      "WHERE icon_url LIKE '/seed-products/%' AND icon_url LIKE '%.jpg';")
