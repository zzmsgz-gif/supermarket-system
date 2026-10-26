-- 2026-10-22 #713：钱包退款流水增加「已花积分等价扣回」金额列。
--
-- 背景：积分防薅（方案D）下，退款时用户先前花掉的、无法从冻结池收回的发放积分，
-- 会按 100分 = ¥1 折算成现金缺口，从退款净额里扣掉（净退款 = 应退 − 缺口）。
-- 此前缺口只落在 return_request.points_deducted，钱包流水 wallet_transaction 只记净额，
-- 用户在「金额明细」看到「支付 −X，退款 +Y」却对不上差额，无从解释。
--
-- 本列把缺口金额（元）一并记进 REFUND 流水：原应退 = amount + points_deducted，
-- 前端据此展示「已花积分等价扣回 ¥Z（原应退 ¥W）」，让账目有据可查。
--
-- 仅退款且发生积分扣回时有值；普通退款 / 支付 / 充值均为 NULL。

ALTER TABLE wallet_transaction
    ADD COLUMN points_deducted DECIMAL(10, 2) DEFAULT NULL
    COMMENT '退款时已花积分等价扣回金额（元）；仅退款且发生积分扣回时有值';
