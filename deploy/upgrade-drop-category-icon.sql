-- 移除分类图标功能：删除 product_category 表的 icon_url 列
-- 执行：mysql -u root -p<密码> <数据库名> < deploy/upgrade-drop-category-icon.sql
ALTER TABLE product_category DROP COLUMN icon_url;
