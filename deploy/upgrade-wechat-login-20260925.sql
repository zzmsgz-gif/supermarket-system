-- 微信小程序登录：sys_user 增加 wx_openid 列（可空，唯一）。
-- 新装机见 deploy/init.sql 与 deploy/railway/init.sql 的 sys_user 建表；
-- 本脚本供【存量库】增量执行，避免 ddl-auto=validate 因缺列导致后端启动即报错。
--
-- 说明：
--   * MySQL 的 UNIQUE 索引允许多个 NULL，故存量用户（wx_openid 全为 NULL）不受影响；
--   * 仅当同一非 NULL openid 出现两次时才报错（本系统首次接入微信，不应发生）；
--   * 若已执行过本脚本，重复执行会报「Duplicate column」——可忽略，属幂等保护。

ALTER TABLE sys_user
    ADD COLUMN wx_openid VARCHAR(64) DEFAULT NULL COMMENT '微信小程序 openid（微信一键登录），NULL=未绑定',
    ADD UNIQUE KEY uk_sys_user_wx_openid (wx_openid);
