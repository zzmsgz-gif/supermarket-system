-- 新增图片相关字段（全站图片上传）
-- 商品分类图标、用户头像、评价晒图

ALTER TABLE product_category
    ADD COLUMN icon_url VARCHAR(500) NULL COMMENT '分类图标URL';

ALTER TABLE sys_user
    ADD COLUMN avatar_url VARCHAR(500) NULL COMMENT '用户头像URL';

ALTER TABLE product_review
    ADD COLUMN image_urls VARCHAR(2000) NULL COMMENT '评价晒图URL，逗号分隔';
