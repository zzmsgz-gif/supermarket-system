-- Supermarket system database bootstrap script.
-- MySQL 8.x recommended.

CREATE DATABASE IF NOT EXISTS supermarket_system
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE supermarket_system;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS product_review;
DROP TABLE IF EXISTS user_coupon;
DROP TABLE IF EXISTS coupon;
DROP TABLE IF EXISTS stock_log;
DROP TABLE IF EXISTS wallet_transaction;
DROP TABLE IF EXISTS payment_record;
DROP TABLE IF EXISTS order_item;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS cart_item;
DROP TABLE IF EXISTS user_address;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS product_category;
DROP TABLE IF EXISTS sys_user;

CREATE TABLE sys_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    username VARCHAR(50) NOT NULL COMMENT 'Login name',
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt password hash',
    nickname VARCHAR(50) DEFAULT NULL COMMENT 'Display name',
    phone VARCHAR(20) DEFAULT NULL COMMENT 'Phone number',
    email VARCHAR(100) DEFAULT NULL COMMENT 'Email address',
    role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT 'USER or ADMIN',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
    balance DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT 'Wallet balance',
    last_login_at DATETIME DEFAULT NULL COMMENT 'Last login time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username),
    UNIQUE KEY uk_sys_user_phone (phone),
    UNIQUE KEY uk_sys_user_email (email),
    KEY idx_sys_user_role_status (role, status),
    CONSTRAINT chk_sys_user_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_sys_user_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_sys_user_balance CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Users and administrators';

CREATE TABLE product_category (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    parent_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Parent category, 0 means root',
    name VARCHAR(50) NOT NULL COMMENT 'Category name',
    sort_no INT NOT NULL DEFAULT 0 COMMENT 'Display order',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_category_name_parent (parent_id, name),
    KEY idx_product_category_parent_status (parent_id, status),
    CONSTRAINT chk_product_category_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_product_category_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Product categories';

CREATE TABLE product (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    category_id BIGINT UNSIGNED NOT NULL COMMENT 'Category id',
    sku VARCHAR(64) NOT NULL COMMENT 'Stock keeping unit',
    name VARCHAR(120) NOT NULL COMMENT 'Product name',
    subtitle VARCHAR(255) DEFAULT NULL COMMENT 'Short description',
    description TEXT COMMENT 'Product description',
    cover_url VARCHAR(500) DEFAULT NULL COMMENT 'Cover image URL',
    price DECIMAL(10, 2) NOT NULL COMMENT 'Current selling price',
    original_price DECIMAL(10, 2) DEFAULT NULL COMMENT 'Original price',
    stock INT NOT NULL DEFAULT 0 COMMENT 'Available stock',
    low_stock_threshold INT NOT NULL DEFAULT 10 COMMENT 'Alert when stock <= this',
    sales INT NOT NULL DEFAULT 0 COMMENT 'Sold quantity',
    unit VARCHAR(20) NOT NULL DEFAULT 'piece' COMMENT 'piece, kg, box, etc.',
    status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE' COMMENT 'ON_SALE, OFF_SALE, DRAFT',
    brand VARCHAR(100) DEFAULT NULL COMMENT 'Brand name',
    is_hot TINYINT NOT NULL DEFAULT 0 COMMENT '1 = hot product',
    is_new TINYINT NOT NULL DEFAULT 0 COMMENT '1 = new arrival',
    sort_no INT NOT NULL DEFAULT 0 COMMENT 'Display order',
    tags VARCHAR(255) DEFAULT NULL COMMENT 'Comma separated marketing tags',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_sku (sku),
    KEY idx_product_category_status (category_id, status),
    KEY idx_product_name (name),
    CONSTRAINT fk_product_category
        FOREIGN KEY (category_id) REFERENCES product_category (id),
    CONSTRAINT chk_product_price CHECK (price >= 0),
    CONSTRAINT chk_product_original_price CHECK (original_price IS NULL OR original_price >= 0),
    CONSTRAINT chk_product_stock CHECK (stock >= 0),
    CONSTRAINT chk_product_sales CHECK (sales >= 0),
    CONSTRAINT chk_product_status CHECK (status IN ('ON_SALE', 'OFF_SALE', 'DRAFT')),
    CONSTRAINT chk_product_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Products';

CREATE TABLE product_image (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    product_id BIGINT UNSIGNED NOT NULL COMMENT 'Owner product id',
    url VARCHAR(500) NOT NULL COMMENT 'Image URL',
    sort_no INT NOT NULL DEFAULT 0 COMMENT 'Display order',
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_product_image_product (product_id),
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT chk_product_image_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Product image gallery';

CREATE TABLE product_sku (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    product_id BIGINT UNSIGNED NOT NULL COMMENT 'Owner product id',
    spec_json TEXT COMMENT 'Selected specs, e.g. {"color":"red","size":"M"}',
    sku_code VARCHAR(64) DEFAULT NULL COMMENT 'SKU code',
    image VARCHAR(500) DEFAULT NULL COMMENT 'SKU image',
    sort_no INT NOT NULL DEFAULT 0 COMMENT 'Display order',
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_product_sku_product (product_id),
    CONSTRAINT fk_product_sku_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT chk_product_sku_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Product SKU / specs';

CREATE TABLE product_attribute (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    product_id BIGINT UNSIGNED NOT NULL COMMENT 'Owner product id',
    attr_name VARCHAR(50) NOT NULL COMMENT 'Attribute name',
    attr_value VARCHAR(200) NOT NULL COMMENT 'Attribute value',
    sort_no INT NOT NULL DEFAULT 0 COMMENT 'Display order',
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_product_attribute_product (product_id),
    CONSTRAINT fk_product_attribute_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT chk_product_attribute_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Product attributes / spec table';

CREATE TABLE user_address (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'Owner user id',
    receiver_name VARCHAR(50) NOT NULL COMMENT 'Receiver name',
    receiver_phone VARCHAR(20) NOT NULL COMMENT 'Receiver phone',
    province VARCHAR(50) NOT NULL,
    city VARCHAR(50) NOT NULL,
    district VARCHAR(50) NOT NULL,
    detail_address VARCHAR(255) NOT NULL,
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '1 default, 0 not default',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_address_user (user_id),
    CONSTRAINT fk_user_address_user
        FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT chk_user_address_default CHECK (is_default IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User delivery addresses';

CREATE TABLE cart_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'Owner user id',
    product_id BIGINT UNSIGNED NOT NULL COMMENT 'Product id',
    quantity INT NOT NULL DEFAULT 1 COMMENT 'Quantity',
    selected TINYINT NOT NULL DEFAULT 1 COMMENT '1 selected, 0 not selected',
    sku_spec VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'Selected SKU spec snapshot (empty = no spec)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_user_product (user_id, product_id, sku_spec),
    KEY idx_cart_user_selected (user_id, selected),
    CONSTRAINT fk_cart_user
        FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_cart_product
        FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT chk_cart_quantity CHECK (quantity > 0),
    CONSTRAINT chk_cart_selected CHECK (selected IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Shopping cart items';

CREATE TABLE orders (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    order_no VARCHAR(32) NOT NULL COMMENT 'Business order number',
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'Buyer user id',
    total_amount DECIMAL(10, 2) NOT NULL COMMENT 'Product total',
    freight_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT 'Freight',
    discount_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT 'Discount',
    pay_amount DECIMAL(10, 2) NOT NULL COMMENT 'Final amount',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT 'Order status',
    payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID' COMMENT 'UNPAID, PAID, REFUNDED',
    receiver_name VARCHAR(50) NOT NULL COMMENT 'Address snapshot',
    receiver_phone VARCHAR(20) NOT NULL COMMENT 'Address snapshot',
    receiver_address VARCHAR(300) NOT NULL COMMENT 'Address snapshot',
    remark VARCHAR(500) DEFAULT NULL COMMENT 'Buyer remark',
    ship_company VARCHAR(50) DEFAULT NULL COMMENT 'Express company',
    ship_no VARCHAR(64) DEFAULT NULL COMMENT 'Express tracking number',
    refund_status VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT 'NONE, APPLYING, APPROVED, REJECTED',
    refund_reason VARCHAR(255) DEFAULT NULL COMMENT 'Buyer refund reason',
    refund_remark VARCHAR(255) DEFAULT NULL COMMENT 'Admin review remark',
    refunded_at DATETIME DEFAULT NULL COMMENT 'Refund finished time',
    user_coupon_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Used user coupon id',
    activity_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Applied activity id',
    activity_discount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT 'Activity discount amount',
    activity_name VARCHAR(120) DEFAULT NULL COMMENT 'Applied activity name snapshot',
    paid_at DATETIME DEFAULT NULL,
    shipped_at DATETIME DEFAULT NULL,
    completed_at DATETIME DEFAULT NULL,
    canceled_at DATETIME DEFAULT NULL,
    closed_at DATETIME DEFAULT NULL COMMENT 'Timeout close time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_no (order_no),
    KEY idx_orders_user_status_created (user_id, status, created_at),
    KEY idx_orders_status_created (status, created_at),
    KEY idx_orders_refund_status (refund_status),
    CONSTRAINT fk_orders_user
        FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT chk_orders_amounts CHECK (
        total_amount >= 0
        AND freight_amount >= 0
        AND discount_amount >= 0
        AND pay_amount >= 0
    ),
    CONSTRAINT chk_orders_status CHECK (
        status IN (
            'PENDING_PAYMENT',
            'PAID',
            'SHIPPED',
            'COMPLETED',
            'CANCELED',
            'CLOSED'
        )
    ),
    CONSTRAINT chk_orders_payment_status CHECK (payment_status IN ('UNPAID', 'PAID', 'REFUNDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Orders';

CREATE TABLE order_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    order_id BIGINT UNSIGNED NOT NULL COMMENT 'Order id',
    product_id BIGINT UNSIGNED NOT NULL COMMENT 'Product id',
    product_name VARCHAR(120) NOT NULL COMMENT 'Product name snapshot',
    product_sku VARCHAR(64) NOT NULL COMMENT 'SKU snapshot',
    product_cover_url VARCHAR(500) DEFAULT NULL COMMENT 'Cover URL snapshot',
    sku_spec VARCHAR(255) DEFAULT NULL COMMENT 'Selected SKU spec snapshot',
    product_price DECIMAL(10, 2) NOT NULL COMMENT 'Price snapshot',
    quantity INT NOT NULL COMMENT 'Purchased quantity',
    subtotal_amount DECIMAL(10, 2) NOT NULL COMMENT 'Item subtotal',
    PRIMARY KEY (id),
    KEY idx_order_item_order (order_id),
    KEY idx_order_item_product (product_id),
    CONSTRAINT fk_order_item_order
        FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_item_product
        FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT chk_order_item_price CHECK (product_price >= 0),
    CONSTRAINT chk_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_item_subtotal CHECK (subtotal_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Order items';

CREATE TABLE payment_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    order_id BIGINT UNSIGNED NOT NULL COMMENT 'Order id',
    payment_no VARCHAR(64) NOT NULL COMMENT 'Payment business number',
    channel VARCHAR(20) NOT NULL DEFAULT 'BALANCE' COMMENT 'BALANCE, MOCK, ALIPAY, WECHAT',
    amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT 'PENDING, SUCCESS, FAILED, REFUNDED',
    paid_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_record_payment_no (payment_no),
    UNIQUE KEY uk_payment_record_order (order_id),
    CONSTRAINT fk_payment_record_order
        FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT chk_payment_record_amount CHECK (amount >= 0),
    CONSTRAINT chk_payment_record_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Payment records';

CREATE TABLE wallet_transaction (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    transaction_no VARCHAR(64) NOT NULL COMMENT 'Wallet transaction number',
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'Owner user id',
    order_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Related order id',
    type VARCHAR(20) NOT NULL COMMENT 'RECHARGE, PAYMENT, REFUND',
    amount DECIMAL(10, 2) NOT NULL COMMENT 'Transaction amount',
    balance_before DECIMAL(10, 2) NOT NULL COMMENT 'Balance before transaction',
    balance_after DECIMAL(10, 2) NOT NULL COMMENT 'Balance after transaction',
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS, FAILED',
    remark VARCHAR(255) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_transaction_no (transaction_no),
    KEY idx_wallet_user_created (user_id, created_at),
    KEY idx_wallet_order (order_id),
    CONSTRAINT fk_wallet_transaction_user
        FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_wallet_transaction_order
        FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT chk_wallet_transaction_type CHECK (type IN ('RECHARGE', 'PAYMENT', 'REFUND')),
    CONSTRAINT chk_wallet_transaction_status CHECK (status IN ('SUCCESS', 'FAILED')),
    CONSTRAINT chk_wallet_transaction_amount CHECK (amount > 0),
    CONSTRAINT chk_wallet_transaction_balance CHECK (balance_before >= 0 AND balance_after >= 0)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Wallet transaction logs';

CREATE TABLE recharge_order (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Recharge orders (simulated payment)';


CREATE TABLE stock_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    product_id BIGINT UNSIGNED NOT NULL COMMENT 'Product id',
    order_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Related order id',
    change_quantity INT NOT NULL COMMENT 'Positive in, negative out',
    stock_before INT NOT NULL COMMENT 'Stock before change',
    stock_after INT NOT NULL COMMENT 'Stock after change',
    biz_type VARCHAR(30) NOT NULL COMMENT 'INIT, PURCHASE, ORDER_DEDUCT, CANCEL_RETURN, MANUAL',
    operator_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Operator user id',
    remark VARCHAR(255) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_stock_log_product_created (product_id, created_at),
    KEY idx_stock_log_order (order_id),
    CONSTRAINT fk_stock_log_product
        FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_stock_log_order
        FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_stock_log_operator
        FOREIGN KEY (operator_id) REFERENCES sys_user (id),
    CONSTRAINT chk_stock_log_stock CHECK (stock_before >= 0 AND stock_after >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Inventory change logs';

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

INSERT INTO coupon (name, threshold_amount, discount_amount, total_count, start_time, end_time, status) VALUES
    ('新用户满 50 减 10', 50.00, 10.00, 100, NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), 1),
    ('超市大促满 100 减 25', 100.00, 25.00, 100, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 1),
    ('日常满 30 减 5', 30.00, 5.00, 0, NOW(), DATE_ADD(NOW(), INTERVAL 180 DAY), 1);

-- 注册自动发放的新人券：id 固定为 5，与 application.yml 的 app.new-user-coupon-id 对应（满10减10，30天有效，不限量）
INSERT INTO coupon (id, name, threshold_amount, discount_amount, total_count, start_time, end_time, status) VALUES
    (5, '新人专享券', 10.00, 10.00, 9999, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 1);

INSERT INTO sys_user (
    username,
    password_hash,
    nickname,
    role,
    status
) VALUES (
    'admin',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoO4s0J0kX0GfQp3R9GQ5P4GfKf5M7D6fK',
    'System Administrator',
    'ADMIN',
    1
);

INSERT INTO product_category (parent_id, name, sort_no, status) VALUES
    (0, '生鲜食品', 10, 1),
    (0, '酒水饮料', 20, 1),
    (0, '休闲零食', 30, 1),
    (0, '日用百货', 40, 1);

INSERT INTO product (
    category_id,
    sku,
    name,
    subtitle,
    price,
    original_price,
    stock,
    unit,
    status
) VALUES
    (
        (SELECT id FROM product_category WHERE name = '生鲜食品' AND parent_id = 0),
        'FRESH-APPLE-001',
        '红富士苹果',
        '脆甜多汁，新鲜称重',
        12.80,
        15.80,
        100,
        '千克',
        'ON_SALE'
    ),
    (
        (SELECT id FROM product_category WHERE name = '酒水饮料' AND parent_id = 0),
        'DRINK-WATER-001',
        '矿泉水',
        '550ml 瓶装饮用水',
        2.50,
        3.00,
        500,
        '瓶',
        'ON_SALE'
    ),
    (
        (SELECT id FROM product_category WHERE name = '休闲零食' AND parent_id = 0),
        'SNACK-CHIPS-001',
        '原味薯片',
        '酥脆原味休闲零食',
        6.90,
        8.90,
        200,
        '袋',
        'ON_SALE'
    );

CREATE TABLE activity (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    name VARCHAR(120) NOT NULL COMMENT 'Activity name',
    type VARCHAR(20) NOT NULL COMMENT 'FULL_REDUCTION | DISCOUNT',
    scope VARCHAR(20) NOT NULL COMMENT 'ALL | CATEGORY | PRODUCT',
    category_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Scope=CATEGORY',
    product_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Scope=PRODUCT',
    threshold DECIMAL(10, 2) DEFAULT NULL COMMENT 'FULL_REDUCTION threshold',
    discount DECIMAL(10, 2) DEFAULT NULL COMMENT 'FULL_REDUCTION amount | DISCOUNT rate(0.9=9折)',
    start_time DATETIME NOT NULL COMMENT 'Active from',
    end_time DATETIME NOT NULL COMMENT 'Active to',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
    priority INT NOT NULL DEFAULT 0 COMMENT 'Higher = matched first on tie',
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_activity_scope (scope),
    KEY idx_activity_status_time (status, start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='营销活动';

CREATE TABLE page_dwell (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    user_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Anonymous when null',
    product_id BIGINT UNSIGNED NOT NULL COMMENT 'Product id',
    seconds INT NOT NULL COMMENT 'Dwell seconds',
    source VARCHAR(30) DEFAULT NULL COMMENT 'e.g. detail',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_page_dwell_user (user_id),
    KEY idx_page_dwell_product (product_id),
    CONSTRAINT fk_page_dwell_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_page_dwell_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='页面停留时长埋点';

CREATE TABLE announcement (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    title VARCHAR(120) NOT NULL COMMENT '公告标题',
    content VARCHAR(500) NOT NULL COMMENT '公告内容/摘要',
    type VARCHAR(20) NOT NULL DEFAULT 'NOTICE' COMMENT 'NOTICE/PROMOTION/ACTIVITY/WARNING',
    sort_order INT NOT NULL DEFAULT 0 COMMENT 'Display order, asc',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
    publish_time DATETIME NOT NULL COMMENT 'Publish time',
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_announcement_enabled_sort (enabled, sort_order, publish_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商城公告';

SET FOREIGN_KEY_CHECKS = 1;
