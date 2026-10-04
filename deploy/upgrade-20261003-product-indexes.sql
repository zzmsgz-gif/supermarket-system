-- 2026-10-03 性能优化：product 表索引
-- 加速公开商品搜索 (kind IS NULL OR kind='NORMAL') 与分类楼层 (status/deleted/category_id) 过滤。
-- 幂等：IF NOT EXISTS，可重复执行。
-- 注意：ddl-auto=validate 不会自动建索引，必须在存量库手动执行本文件（见服务器同步步骤）。

CREATE INDEX IF NOT EXISTS idx_product_kind
    ON product (kind);

CREATE INDEX IF NOT EXISTS idx_product_del_status_cat
    ON product (deleted, status, category_id);
