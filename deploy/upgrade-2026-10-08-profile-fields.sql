-- ============================================================================
-- 2026-10-08 个人资料：性别 / 生日字段（第 9 条）
-- ----------------------------------------------------------------------------
-- 为什么 birthday 用 VARCHAR(10) 而不是 DATE：
--   生日只有「日」没有「时区」。存 DATE 会让某些 ORM 在序列化/跨时区时偏移一天
--   （前端显示 3 月 1 日、后端存 2 月 28 日这类经典 bug），而且它允许为空
--   —— 很多人不填生日，用 NOT NULL 就要给个假值。校验放在应用层（不能晚于今天）。
--
-- 为什么 gender 允许 NULL 而不是 NOT NULL：
--   不填性别是常态，填 'SECRET' 或 NULL 都行；前端都按「未设置」处理。
-- ============================================================================

ALTER TABLE sys_user
    ADD COLUMN gender   VARCHAR(10) DEFAULT NULL COMMENT 'MALE / FEMALE / SECRET' AFTER email,
    ADD COLUMN birthday VARCHAR(10) DEFAULT NULL COMMENT 'Birthday yyyy-MM-dd, kept as string to avoid timezone shifts' AFTER gender;
