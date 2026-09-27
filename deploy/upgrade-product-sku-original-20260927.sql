-- 规格吊牌价：给商品多规格（product_sku）增加「规格原价(划线价)」列
-- 背景：此前详情页的划线价绑定的是商品级 original_price，导致同一商品不同规格都显示同一个划线价。
-- 语义：original_price 为 NULL 表示该规格跟随商品原价（无规格级划线价时回退商品级）；
--        非 NULL 则按「规格价 × (商品原价 / 商品基准价)」推导，随规格变化。
-- 重塑脚本见 deploy/_reshape_sku_original.py（同时更新运行库与 seed-data.sql）。
ALTER TABLE product_sku
    ADD COLUMN original_price DECIMAL(10, 2) DEFAULT NULL COMMENT '规格吊牌价（划线价）；NULL=跟随商品原价' AFTER price;
