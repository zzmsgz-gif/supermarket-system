-- 2026-10-22 #712 对账修复：纠正历史遗留的「过度冻结」脏数据。
--
-- 背景：旧版 freezeForReturn 未把冻结分 clamp 到可用积分，导致部分用户的 sys_user.frozen_points
-- 被累加到超过总积分（例如 frozen=194 但 points=24）。后果：
--   1) 积分页显示「冻结 194 > 累计 24」自相矛盾；
--   2) 可用积分 = max(0, points - frozen) 被算成 0，结算页「积分抵扣」勾选被禁用，明明有积分却用不了。
--
-- 修复：以 return_request 的冻结分（方案D-C 落库的 frozen_points）为准，重新计算每个用户的冻结分 =
-- 所有「未结」售后单（APPLYING / WAITING_SHIP / SHIPPED_BACK，即退款审核中、积分仍被冻结的状态）之和。
-- 已驳回(REJECTED→releaseFreeze)或已通过(APPROVED→recoverForRefund)的售后单不再计入。
--
-- 注意：本脚本只修正 frozen_points；可用积分 availablePoints 由后端 profile 实时算（points - frozen），
-- 修复后自动恢复。运行前请先备份，并在低峰期执行。

-- 1) 把「有未结售后单」的用户冻结分重算为这些单的 frozen_points 之和
UPDATE sys_user u
JOIN (
    SELECT r.user_id, SUM(COALESCE(r.frozen_points, 0)) AS fp
    FROM return_request r
    WHERE r.status IN ('APPLYING', 'WAITING_SHIP', 'SHIPPED_BACK')
    GROUP BY r.user_id
) s ON s.user_id = u.id
SET u.frozen_points = s.fp;

-- 2) 把「没有任何未结售后单」的用户的冻结分清零（步骤1的 JOIN 覆盖不到这些人）
UPDATE sys_user
SET frozen_points = 0
WHERE id NOT IN (
    SELECT DISTINCT user_id FROM return_request
    WHERE status IN ('APPLYING', 'WAITING_SHIP', 'SHIPPED_BACK')
);
