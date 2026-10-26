-- 修复 orders.refund_status 检查约束
-- 背景：代码实际用到 7 个值（NONE/APPLYING/APPROVED/REJECTED/WAITING_SHIP/SHIPPED_BACK/PARTIAL），
-- 但原约束只放行 4 个值，导致管理端「需用户寄回」(WAITING_SHIP)、用户寄回(SHIPPED_BACK)、
-- 按商品行部分退款(PARTIAL) 整事务回滚、操作失败。
-- 本脚本把约束扩展到全部 7 个值。

ALTER TABLE orders DROP CONSTRAINT chk_orders_refund_status;
ALTER TABLE orders ADD CONSTRAINT chk_orders_refund_status
    CHECK (refund_status IN ('NONE', 'APPLYING', 'APPROVED', 'REJECTED', 'WAITING_SHIP', 'SHIPPED_BACK', 'PARTIAL'));
