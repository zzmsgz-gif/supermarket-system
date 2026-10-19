-- 2026-10-13 会员让利来源快照
-- 订单详情页把「会员让利」拆成「会员优惠」（商品会员价）与「会员折扣」（等级折扣）两行分开列，
-- 需要每行记录下单时的让利来源。历史订单保持 NULL（前端整单回退旧的合并展示，不错标）。
-- ⚠️ 必须在重启后端之前执行（ddl-auto: validate，缺列启动即失败）。
ALTER TABLE order_item
    ADD COLUMN member_source VARCHAR(10) DEFAULT NULL
        COMMENT '会员让利来源快照: member=商品会员价 tier=等级折扣 NULL=无(含秒杀成交)'
        AFTER flash_sale_id;
