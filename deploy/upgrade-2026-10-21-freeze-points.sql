-- 升级脚本：方案D（C+B 组合）防「先花积分再退单」套利
-- 日期：2026-10-21
-- 说明：申请退款即冻结该订单产生的发放积分（C），退款时冻结不足（申请前已花掉）的部分
--       等价从现金退款扣回（B），商家零风险。
-- 注意：本项目 ddl-auto=validate，加列必须先在 DB 执行本脚本，再部署带新实体的 jar。

-- 1) 用户表：冻结积分（已被退款申请锁定的、暂不可用的发放积分）
ALTER TABLE sys_user ADD COLUMN frozen_points BIGINT NOT NULL DEFAULT 0;

-- 2) 售后单：本单在 apply 时冻结的积分额。
--    用途：驳回/取消时按此额释放；退款回收时作为冻结池回收对账基准。
ALTER TABLE return_request ADD COLUMN frozen_points BIGINT NOT NULL DEFAULT 0;

-- 3) 售后单：方案D-B 退款时已花掉、被现金等价扣回的发放积分额（分）。
--    持久化 recoverForRefund 返回的 shortfall，供前端金额明细展示「已花积分等价扣回」。
--    0 = 无扣回；仅整单/按行退款时写入，申请/审核态恒 0。
ALTER TABLE return_request ADD COLUMN points_deducted BIGINT NOT NULL DEFAULT 0;
