-- Recharge feature upgrade: adds the recharge_order table (simulated Alipay/WeChat top-up).
-- Run this on the EXISTING supermarket_system database (MySQL 8.x).
-- The backend ddl-auto is `validate`, so this table MUST exist before the new jar starts.

USE supermarket_system;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS recharge_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    order_no VARCHAR(64) NOT NULL COMMENT 'Recharge order business number',
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'Owner user id',
    amount DECIMAL(10, 2) NOT NULL COMMENT 'Recharge amount',
    method VARCHAR(20) NOT NULL COMMENT 'ALIPAY or WECHAT (simulated)',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, PAID, CANCELLED, EXPIRED',
    expire_at DATETIME NOT NULL COMMENT 'Order valid until; unpaid after this becomes EXPIRED',
    paid_at DATETIME DEFAULT NULL COMMENT 'Paid time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_recharge_order_no (order_no),
    KEY idx_recharge_user (user_id),
    KEY idx_recharge_status_expire (status, expire_at),
    CONSTRAINT chk_recharge_method CHECK (method IN ('ALIPAY', 'WECHAT')),
    CONSTRAINT chk_recharge_status CHECK (status IN ('PENDING', 'PAID', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT chk_recharge_amount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Recharge orders (simulated payment)';

SET FOREIGN_KEY_CHECKS = 1;
