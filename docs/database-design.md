# Supermarket System Database Design

This document describes the first usable database design for the supermarket shopping system. The matching executable SQL file is `deploy/init.sql`.

## Scope

The MVP supports:

- User registration and login.
- Product category browsing.
- Product listing, search, details, and admin maintenance.
- Shopping cart add, update, select, and delete.
- Order creation, order detail query, and admin order management.
- Stock deduction and stock change history.
- Mock payment record for later real payment integration.

## Tables

### sys_user

Stores frontend users and administrators.

Important fields:

- `username`: unique login name.
- `password_hash`: encrypted password, recommended BCrypt.
- `role`: `USER` or `ADMIN`.
- `status`: `1` enabled, `0` disabled.
- `deleted`: logical delete flag.

### product_category

Stores product categories. `parent_id = 0` means a root category.

Important fields:

- `name`: category name.
- `sort_no`: display order.
- `status`: whether the category is visible.

### product

Stores sellable products.

Important fields:

- `category_id`: linked category.
- `sku`: unique product code.
- `price`: current selling price.
- `stock`: available stock.
- `sales`: sold quantity.
- `status`: `ON_SALE`, `OFF_SALE`, or `DRAFT`.

### user_address

Stores user delivery addresses.

Order records also keep address snapshots, so old orders remain stable after users modify addresses.

### cart_item

Stores shopping cart rows.

Rules:

- One user can only have one cart row for the same product.
- `quantity` must be greater than 0.
- `selected` is used when submitting only selected cart items.

### orders

Stores order master records.

Important fields:

- `order_no`: public business order number.
- `total_amount`: product total.
- `freight_amount`: shipping fee.
- `discount_amount`: discount amount.
- `pay_amount`: final payable amount.
- `status`: order lifecycle status.
- `payment_status`: payment state.
- Receiver fields are address snapshots.

Order statuses:

- `PENDING_PAYMENT`: created but unpaid.
- `PAID`: paid and waiting for shipment or pickup.
- `SHIPPED`: shipped.
- `COMPLETED`: completed.
- `CANCELED`: canceled by user or admin.
- `CLOSED`: closed by timeout or after-sale flow.

### order_item

Stores products inside an order. Product name, SKU, cover, and price are snapshots from order creation time.

### payment_record

Stores payment records. The MVP can use `MOCK` channel. Later it can be connected to Alipay, WeChat Pay, or other payment providers.

### stock_log

Stores inventory changes.

Common business types:

- `INIT`: initial stock.
- `PURCHASE`: purchase stock-in.
- `ORDER_DEDUCT`: order stock deduction.
- `CANCEL_RETURN`: canceled order stock return.
- `MANUAL`: admin manual adjustment.

### coupon

Stores coupon templates.

Important fields:

- `threshold_amount`: minimum order total required to use the coupon.
- `discount_amount`: money reduced when the coupon is used.
- `total_count`: `0` means unlimited issuance.
- `received_count`: how many users already claimed it.
- `start_time` / `end_time`: valid period.

### user_coupon

Stores coupons claimed by users.

Important fields:

- `status`: `UNUSED`, `USED`, or `EXPIRED`.
- `order_id`: order that consumed the coupon.
- Unique key on `(user_id, coupon_id)` prevents duplicate claiming.

### product_review

Stores product reviews. Reviews can only be created from `COMPLETED` orders.

Important fields:

- `order_item_id`: unique, so one order item can only be reviewed once.
- `rating`: 1 to 5.

## P0 Columns Added To Existing Tables

### orders

- `ship_company`: express company name.
- `ship_no`: express tracking number.
- `refund_status`: `NONE`, `APPLYING`, `APPROVED`, or `REJECTED`.
- `refund_reason`: buyer reason.
- `refund_remark`: admin review remark.
- `refunded_at`: refund finished time.
- `user_coupon_id`: coupon used by this order.
- `closed_at`: timeout close time.

### product

- `low_stock_threshold`: alert when `stock` is less than or equal to this value.

## Main Relationships

```text
sys_user 1 -> n user_address
sys_user 1 -> n cart_item
sys_user 1 -> n orders
product_category 1 -> n product
product 1 -> n cart_item
orders 1 -> n order_item
product 1 -> n order_item
product 1 -> n stock_log
orders 1 -> 0..1 payment_record
coupon 1 -> n user_coupon
sys_user 1 -> n user_coupon
orders 0..1 -> n user_coupon
orders 1 -> n product_review
order_item 1 -> 0..1 product_review
product 1 -> n product_review
```

## Implementation Notes

- Use transactions when creating orders: read cart items, validate product status and stock, create order, create order items, deduct stock, write stock logs, then clear submitted cart rows.
- Do not calculate money with `float` or `double` in backend code. Use Java `BigDecimal`.
- Product stock should be updated with a guarded SQL condition, such as `stock >= quantity`, to avoid overselling.
- User passwords in seed data are placeholders. Replace them with real BCrypt hashes when implementing login.
