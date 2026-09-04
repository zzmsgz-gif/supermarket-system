-- Supermarket system database bootstrap script.
-- MySQL 8.x recommended.

CREATE DATABASE IF NOT EXISTS supermarket_system
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE supermarket_system;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS stock_log;
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
    CONSTRAINT chk_sys_user_status CHECK (status IN (0, 1))
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
    sales INT NOT NULL DEFAULT 0 COMMENT 'Sold quantity',
    unit VARCHAR(20) NOT NULL DEFAULT 'piece' COMMENT 'piece, kg, box, etc.',
    status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE' COMMENT 'ON_SALE, OFF_SALE, DRAFT',
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
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_user_product (user_id, product_id),
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
    paid_at DATETIME DEFAULT NULL,
    shipped_at DATETIME DEFAULT NULL,
    completed_at DATETIME DEFAULT NULL,
    canceled_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_no (order_no),
    KEY idx_orders_user_status_created (user_id, status, created_at),
    KEY idx_orders_status_created (status, created_at),
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
    channel VARCHAR(20) NOT NULL DEFAULT 'MOCK' COMMENT 'MOCK, ALIPAY, WECHAT',
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
    (0, 'Fresh Food', 10, 1),
    (0, 'Beverages', 20, 1),
    (0, 'Snacks', 30, 1),
    (0, 'Household', 40, 1);

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
        (SELECT id FROM product_category WHERE name = 'Fresh Food' AND parent_id = 0),
        'FRESH-APPLE-001',
        'Red Apples',
        'Crisp and sweet',
        12.80,
        15.80,
        100,
        'kg',
        'ON_SALE'
    ),
    (
        (SELECT id FROM product_category WHERE name = 'Beverages' AND parent_id = 0),
        'DRINK-WATER-001',
        'Mineral Water',
        '550ml bottle',
        2.50,
        3.00,
        500,
        'bottle',
        'ON_SALE'
    ),
    (
        (SELECT id FROM product_category WHERE name = 'Snacks' AND parent_id = 0),
        'SNACK-CHIPS-001',
        'Potato Chips',
        'Original flavor',
        6.90,
        8.90,
        200,
        'bag',
        'ON_SALE'
    );

SET FOREIGN_KEY_CHECKS = 1;
