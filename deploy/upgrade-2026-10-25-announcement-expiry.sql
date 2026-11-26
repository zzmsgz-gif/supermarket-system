-- 公告时效窗口（2026-10-25）：effective_time 之前不展示，expiry_time 之后自动下架。
-- 前台 /announcements 仅返回时效内的公告；后台 /admin/announcements 仍返回全部（含已到期）便于管理。
-- 存量公告两列均为 NULL → 维持原行为（一直展示、永不自动过期），由运营在后台按需设置。
ALTER TABLE `announcement`
  ADD COLUMN `effective_time` DATETIME NULL DEFAULT NULL COMMENT '生效时间，NULL 表示从发布起一直生效' AFTER `publish_time`,
  ADD COLUMN `expiry_time`   DATETIME NULL DEFAULT NULL COMMENT '失效时间，NULL 表示永不自动过期' AFTER `effective_time`;
