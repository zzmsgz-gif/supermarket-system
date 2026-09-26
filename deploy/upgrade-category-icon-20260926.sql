-- 恢复分类图标：product_category 加 icon_url 列
-- 背景：该列在 deploy/init.sql 的新装机 DDL 里一直存在，但被 upgrade-drop-category-icon.sql
--       从存量库删掉了，导致「新装机的表有这列、老库没有」的结构漂移，实体也因此不敢加字段。
--       本次统一补回，实体 ProductCategory.iconUrl / 后台表单 / 公开接口一并启用。
-- 执行：mysql -u root -p<密码> --default-character-set=utf8mb4 <数据库名> < deploy/upgrade-category-icon-20260926.sql

ALTER TABLE product_category
    ADD COLUMN icon_url VARCHAR(500) DEFAULT NULL COMMENT '分类图标URL' AFTER name;
