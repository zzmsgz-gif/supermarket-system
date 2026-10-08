-- ============================================================================
-- 2026-10-08 退货/退款申请单（第 13 条：收到货不想要了要走退货流程）
-- ----------------------------------------------------------------------------
-- 为什么要新建表而不是往 orders 上加字段：
--   orders.refund_status 只有 NONE/APPLYING/APPROVED/REJECTED 四值，
--   表达不了「商家已同意、但用户还没寄回」这段中间态。
--   硬塞进 orders 会把**订单主状态**（物流维度）与**退款流程状态**纠缠在一起，
--   而这两条线本就该独立（本项目已决定暂不加物流维度）。
--
-- 状态机：
--   APPLYING ──驳回──> REJECTED（终态）
--      ├──同意 + 不需寄回（质量问题/错发）──> APPROVED ──> 退款
--      └──同意 + 需寄回（不想要了）──────> WAITING_SHIP ──用户填单号──> SHIPPED_BACK
--                                                                   ├──确认收货──> APPROVED ──> 退款
--                                                                   └──驳回──────> REJECTED
--
-- 运费责任（freight_borne_by）：
--   由**商家在审核时判定**，不能让用户自报（否则全选「质量问题」骗运费）。
--   SELLER=商家承担（质量问题/错发）；BUYER=用户承担（七天无理由不想要了）。
-- ============================================================================

CREATE TABLE IF NOT EXISTS return_request (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'Applicant user id',
    order_id BIGINT UNSIGNED NOT NULL COMMENT 'Related order id',
    status VARCHAR(20) NOT NULL COMMENT 'APPLYING, WAITING_SHIP, SHIPPED_BACK, APPROVED, REJECTED',
    type VARCHAR(20) NOT NULL DEFAULT 'REFUND' COMMENT 'REFUND=只要退款, RETURN=退货退款',
    refund_amount DECIMAL(10, 2) NOT NULL COMMENT 'Amount to refund',
    reason VARCHAR(255) DEFAULT NULL COMMENT 'User stated reason',
    remark VARCHAR(500) DEFAULT NULL COMMENT 'User extra note',
    need_return TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether user must ship the goods back',
    freight_borne_by VARCHAR(10) DEFAULT NULL COMMENT 'SELLER / BUYER, decided by merchant at review time',
    express_company VARCHAR(40) DEFAULT NULL COMMENT 'Express company filled by user when shipping back',
    tracking_no VARCHAR(64) DEFAULT NULL COMMENT 'Tracking number filled by user',
    admin_remark VARCHAR(500) DEFAULT NULL COMMENT 'Merchant review note',
    approved_at DATETIME DEFAULT NULL COMMENT 'When merchant approved',
    shipped_back_at DATETIME DEFAULT NULL COMMENT 'When user shipped back',
    completed_at DATETIME DEFAULT NULL COMMENT 'When merchant confirmed receipt and refunded',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_return_order (order_id),
    KEY idx_return_user (user_id, created_at),
    KEY idx_return_status (status),
    CONSTRAINT fk_return_user
        FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_return_order
        FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT chk_return_status CHECK (status IN (
        'APPLYING', 'WAITING_SHIP', 'SHIPPED_BACK', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_return_type CHECK (type IN ('REFUND', 'RETURN')),
    CONSTRAINT chk_return_freight CHECK (
        freight_borne_by IS NULL OR freight_borne_by IN ('SELLER', 'BUYER')),
    CONSTRAINT chk_return_amount CHECK (refund_amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Return/refund requests with ship-back flow';
