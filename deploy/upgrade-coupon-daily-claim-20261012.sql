-- 2026-10-12：修复「每日可领券」第二天领取报 Data conflict
--
-- 背景：user_coupon 原有唯一键 uk_user_coupon_user_coupon (user_id, coupon_id)。
-- 2026-10-10 上线了 claim_type=1（每天可领一次，当天 0 点重置）：
--   · 同日重复领取 → 服务层 existsReceivedSince 拦住（"今日已领取，明天再来"）；
--   · **次日再领** → 服务层放行，INSERT 撞 (user_id, coupon_id) 唯一键
--     → DataIntegrityViolationException → 全局异常兜底成英文 "Data conflict"，
--       前端原样弹出，用户完全看不懂（本次用户反馈）。
--
-- 修法：唯一键改成「同一天」粒度 —— (user_id, coupon_id, DATE(received_at))
--   · 同日连点/重复提交：仍被数据库拦（409 → 中文提示）；
--   · 每日券跨天再领：不再冲突，正常发券；
--   · 每人限领一次券（claim_type=0）：由服务层 findByUserIdAndCouponId 拦截，不依赖此键。
--
-- MySQL 8.0.13+ 支持函数索引（表达式键的一部分），线上/本地均为 8.0.30。
-- Hibernate ddl-auto=validate 只校验表与列，不校验索引/唯一键 → 无需改实体。

ALTER TABLE user_coupon
    DROP INDEX uk_user_coupon_user_coupon,
    ADD UNIQUE KEY uk_user_coupon_user_coupon_day (user_id, coupon_id, (DATE(received_at)));
