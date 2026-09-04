-- P0 upgrade script: adds coupon, user_coupon, product_review tables and new order/product columns.
-- Run this on the existing supermarket_system database (MySQL 8.x).

USE supermarket_system;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. Order shipping / refund columns
ALTER TABLE orders
    ADD COLUMN ship_company VARCHAR(50) DEFAULT NULL COMMENT 'Express company' AFTER remark,
    ADD COLUMN ship_no VARCHAR(64) DEFAULT NULL COMMENT 'Express tracking number' AFTER ship_company,
    ADD COLUMN refund_status VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT 'NONE, APPLYING, APPROVED, REJECTED' AFTER ship_no,
    ADD COLUMN refund_reason VARCHAR(255) DEFAULT NULL COMMENT 'Buyer refund reason' AFTER refund_status,
    ADD COLUMN refund_remark VARCHAR(255) DEFAULT NULL COMMENT 'Admin review remark' AFTER refund_reason,
    ADD COLUMN refunded_at DATETIME DEFAULT NULL COMMENT 'Refund finished time' AFTER refund_remark,
    ADD COLUMN user_coupon_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Used user coupon id' AFTER refunded_at,
    ADD COLUMN closed_at DATETIME DEFAULT NULL COMMENT 'Timeout close time' AFTER canceled_at;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_refund_status CHECK (refund_status IN ('NONE', 'APPLYING', 'APPROVED', 'REJECTED'));

ALTER TABLE orders
    ADD KEY idx_orders_refund_status (refund_status);

-- 2. Low stock threshold on product
ALTER TABLE product
    ADD COLUMN low_stock_threshold INT NOT NULL DEFAULT 10 COMMENT 'Alert when stock <= this' AFTER stock;

-- 3. Coupons
CREATE TABLE coupon (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    name VARCHAR(100) NOT NULL COMMENT 'Display name',
    threshold_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT 'Min order total to use',
    discount_amount DECIMAL(10, 2) NOT NULL COMMENT 'Discount value',
    total_count INT NOT NULL DEFAULT 0 COMMENT 'Total issuable count, 0 = unlimited',
    received_count INT NOT NULL DEFAULT 0 COMMENT 'Already received count',
    start_time DATETIME NOT NULL COMMENT 'Valid from',
    end_time DATETIME NOT NULL COMMENT 'Valid until',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_coupon_status_time (status, start_time, end_time),
    CONSTRAINT chk_coupon_amounts CHECK (threshold_amount >= 0 AND discount_amount > 0 AND discount_amount <= threshold_amount),
    CONSTRAINT chk_coupon_counts CHECK (total_count >= 0 AND received_count >= 0),
    CONSTRAINT chk_coupon_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_coupon_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Coupons';

CREATE TABLE user_coupon (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    coupon_id BIGINT UNSIGNED NOT NULL COMMENT 'Coupon id',
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'Owner user id',
    status VARCHAR(20) NOT NULL DEFAULT 'UNUSED' COMMENT 'UNUSED, USED, EXPIRED',
    order_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Order that consumed this coupon',
    received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_coupon_user_coupon (user_id, coupon_id),
    KEY idx_user_coupon_user_status (user_id, status),
    CONSTRAINT fk_user_coupon_coupon FOREIGN KEY (coupon_id) REFERENCES coupon (id),
    CONSTRAINT fk_user_coupon_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_user_coupon_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT chk_user_coupon_status CHECK (status IN ('UNUSED', 'USED', 'EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User received coupons';

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_user_coupon FOREIGN KEY (user_coupon_id) REFERENCES user_coupon (id);

-- 4. Product reviews
CREATE TABLE product_review (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    order_id BIGINT UNSIGNED NOT NULL COMMENT 'Source order id',
    order_item_id BIGINT UNSIGNED NOT NULL COMMENT 'Source order item id',
    product_id BIGINT UNSIGNED NOT NULL COMMENT 'Product id',
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'Reviewer user id',
    rating TINYINT NOT NULL COMMENT '1 to 5 stars',
    content VARCHAR(1000) DEFAULT NULL COMMENT 'Review text',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_review_order_item (order_item_id),
    KEY idx_product_review_product_created (product_id, created_at),
    CONSTRAINT fk_product_review_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_product_review_order_item FOREIGN KEY (order_item_id) REFERENCES order_item (id),
    CONSTRAINT fk_product_review_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_product_review_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT chk_product_review_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Product reviews';

-- 5. Seed coupons
INSERT INTO coupon (name, threshold_amount, discount_amount, total_count, start_time, end_time, status) VALUES
    ('新用户满 50 减 10', 50.00, 10.00, 100, NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), 1),
    ('超市大促满 100 减 25', 100.00, 25.00, 100, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 1),
    ('日常满 30 减 5', 30.00, 5.00, 0, NOW(), DATE_ADD(NOW(), INTERVAL 180 DAY), 1);

SET FOREIGN_KEY_CHECKS = 1;
