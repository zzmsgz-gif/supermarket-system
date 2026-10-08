-- ============================================================================
-- 2026-10-08 金额流水表（第 7 条：用户端「金额明细」）
-- ----------------------------------------------------------------------------
-- 为什么不用已有的 wallet_transaction：
--   那个表记的是**钱包余额变动**，只有充值/支付/退款会写。
--   而用户想对账时看的是「这单实付多少、优惠抵了多少」——
--   **优惠抵扣不产生余额变动**，在 wallet_transaction 里根本查不到，久了完全对不上账。
--   所以按订单维度另开一张表，一条订单记多条（实付/券/活动/会员/积分/运费/退款）。
--
-- 与 wallet_transaction 的分工：
--   wallet_transaction → 余额怎么变的（对账用，balance_before/after 连续）
--   amount_record      → 订单金额怎么构成的（展示用）
--
-- ⚠️ direction 而非「金额带符号」：支出记正数 + direction=1，收入记正数 + direction=-1。
--    带符号金额在前端拼接 "-¥12.34" 时极易漏符号、也难做求和与筛选。
-- ============================================================================

CREATE TABLE IF NOT EXISTS amount_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'Owner user id',
    order_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Related order id, null for non-order records',
    type VARCHAR(24) NOT NULL COMMENT 'ORDER_PAY, ORDER_REFUND, COUPON_DISCOUNT, ACTIVITY_DISCOUNT, MEMBER_DISCOUNT, POINTS_DISCOUNT, FREIGHT, POINTS_EARN',
    direction INT NOT NULL DEFAULT 1 COMMENT '1 = 支出（付出去）, -1 = 收入（退回来/省下来）',
    amount DECIMAL(10, 2) NOT NULL COMMENT 'Amount, always positive; direction tells the sign',
    title VARCHAR(40) NOT NULL COMMENT 'Display title, e.g. 订单支付 / 满200减50',
    remark VARCHAR(255) DEFAULT NULL COMMENT 'Extra detail: coupon name, activity name, tracking no',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_amount_record_user (user_id, created_at),
    KEY idx_amount_record_order (order_id),
    CONSTRAINT fk_amount_record_user
        FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_amount_record_order
        FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT chk_amount_record_type CHECK (type IN (
        'ORDER_PAY', 'ORDER_REFUND', 'COUPON_DISCOUNT', 'ACTIVITY_DISCOUNT',
        'MEMBER_DISCOUNT', 'POINTS_DISCOUNT', 'FREIGHT', 'POINTS_EARN')),
    CONSTRAINT chk_amount_record_direction CHECK (direction IN (1, -1)),
    CONSTRAINT chk_amount_record_amount CHECK (amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'User-facing amount records (order amount breakdown)';


-- ----------------------------------------------------------------------------

-- ----------------------------------------------------------------------------
-- 历史已付/已发/已完成订单回填（2026-10-08 实际执行过，本地回填 81 条）
-- 幂等：加 NOT EXISTS 守卫，重复执行不会产生重复流水。
-- 说明：流水是**展示数据**，不回填也不影响功能（只是老订单在明细里看不到），
--      但回填后用户一进页面就能对上账，体验差别很大，所以做。
-- ----------------------------------------------------------------------------
INSERT INTO amount_record (user_id, order_id, type, direction, amount, title, remark)
SELECT o.user_id, o.id, t.type, t.direction,
       CASE t.type
         WHEN 'ORDER_PAY'        THEN o.pay_amount
         WHEN 'FREIGHT'          THEN o.freight_amount
         WHEN 'COUPON_DISCOUNT'  THEN o.discount_amount
         WHEN 'ACTIVITY_DISCOUNT'THEN o.activity_discount
         WHEN 'MEMBER_DISCOUNT'  THEN o.member_discount
         WHEN 'POINTS_DISCOUNT'  THEN o.points_discount
       END,
       t.title,
       COALESCE(NULLIF(o.coupon_name,''), NULLIF(o.activity_name,''))
  FROM orders o
  JOIN (
       SELECT 'ORDER_PAY' AS type, 1 AS direction, '订单支付' AS title
    UNION ALL SELECT 'FREIGHT',         1, '运费'
    UNION ALL SELECT 'COUPON_DISCOUNT',  -1, '优惠券抵扣'
    UNION ALL SELECT 'ACTIVITY_DISCOUNT',-1, '活动优惠'
    UNION ALL SELECT 'MEMBER_DISCOUNT', -1, '会员优惠'
    UNION ALL SELECT 'POINTS_DISCOUNT', -1, '积分抵扣'
  ) t
 WHERE o.status IN ('PAID','SHIPPED','COMPLETED')
   AND CASE t.type
         WHEN 'ORDER_PAY'        THEN o.pay_amount
         WHEN 'FREIGHT'          THEN o.freight_amount
         WHEN 'COUPON_DISCOUNT'  THEN o.discount_amount
         WHEN 'ACTIVITY_DISCOUNT'THEN o.activity_discount
         WHEN 'MEMBER_DISCOUNT'  THEN o.member_discount
         WHEN 'POINTS_DISCOUNT'  THEN o.points_discount
       END > 0
   AND NOT EXISTS (
        SELECT 1 FROM amount_record ar WHERE ar.order_id = o.id AND ar.type = t.type
   );
