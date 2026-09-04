# Supermarket System REST API Design

Base URL for backend APIs:

```text
/api
```

## Response Format

All APIs return the same wrapper:

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

Common codes:

- `0`: success.
- `400`: invalid request.
- `401`: not logged in or token expired.
- `403`: permission denied.
- `404`: resource not found.
- `409`: business conflict, such as insufficient stock.
- `500`: server error.

Paged list format:

```json
{
  "items": [],
  "page": 1,
  "size": 10,
  "total": 100,
  "pages": 10
}
```

## Authentication

### Register

```http
POST /api/auth/register
```

Request:

```json
{
  "username": "zhangsan",
  "password": "123456",
  "nickname": "Zhang San",
  "phone": "13800000000"
}
```

### Login

```http
POST /api/auth/login
```

Request:

```json
{
  "username": "admin",
  "password": "123456"
}
```

Response data:

```json
{
  "token": "jwt-token",
  "user": {
    "id": 1,
    "username": "admin",
    "nickname": "System Administrator",
    "role": "ADMIN"
  }
}
```

### Current User

```http
GET /api/auth/me
Authorization: Bearer <token>
```

## Product Categories

### Public Category List

```http
GET /api/categories
```

Response data:

```json
[
  {
    "id": 1,
    "parentId": 0,
    "name": "Fresh Food",
    "sortNo": 10
  }
]
```

### Admin Create Category

```http
POST /api/admin/categories
Authorization: Bearer <admin-token>
```

Request:

```json
{
  "parentId": 0,
  "name": "Fresh Food",
  "sortNo": 10,
  "status": 1
}
```

### Admin Update Category

```http
PUT /api/admin/categories/{id}
Authorization: Bearer <admin-token>
```

### Admin Delete Category

```http
DELETE /api/admin/categories/{id}
Authorization: Bearer <admin-token>
```

## Products

### Public Product List

```http
GET /api/products?page=1&size=10&categoryId=1&keyword=apple
```

Response data:

```json
{
  "items": [
    {
      "id": 1,
      "categoryId": 1,
      "sku": "FRESH-APPLE-001",
      "name": "Red Apples",
      "subtitle": "Crisp and sweet",
      "coverUrl": null,
      "price": 12.80,
      "originalPrice": 15.80,
      "stock": 100,
      "sales": 0,
      "unit": "kg",
      "status": "ON_SALE"
    }
  ],
  "page": 1,
  "size": 10,
  "total": 1,
  "pages": 1
}
```

### Public Product Detail

```http
GET /api/products/{id}
```

### Admin Create Product

```http
POST /api/admin/products
Authorization: Bearer <admin-token>
```

Request:

```json
{
  "categoryId": 1,
  "sku": "FRESH-APPLE-001",
  "name": "Red Apples",
  "subtitle": "Crisp and sweet",
  "description": "Detailed product description",
  "coverUrl": "https://example.com/apple.jpg",
  "price": 12.80,
  "originalPrice": 15.80,
  "stock": 100,
  "unit": "kg",
  "status": "ON_SALE"
}
```

### Admin Update Product

```http
PUT /api/admin/products/{id}
Authorization: Bearer <admin-token>
```

### Admin Update Product Status

```http
PATCH /api/admin/products/{id}/status
Authorization: Bearer <admin-token>
```

Request:

```json
{
  "status": "OFF_SALE"
}
```

### Admin Delete Product

```http
DELETE /api/admin/products/{id}
Authorization: Bearer <admin-token>
```

## Cart

All cart APIs require login.

### Cart List

```http
GET /api/cart
Authorization: Bearer <token>
```

### Add Product To Cart

```http
POST /api/cart/items
Authorization: Bearer <token>
```

Request:

```json
{
  "productId": 1,
  "quantity": 2
}
```

If the product already exists in the cart, increase its quantity.

### Update Cart Item

```http
PUT /api/cart/items/{id}
Authorization: Bearer <token>
```

Request:

```json
{
  "quantity": 3,
  "selected": true
}
```

### Delete Cart Item

```http
DELETE /api/cart/items/{id}
Authorization: Bearer <token>
```

### Select Cart Items

```http
PATCH /api/cart/items/selection
Authorization: Bearer <token>
```

Request:

```json
{
  "itemIds": [1, 2],
  "selected": true
}
```

## Addresses

All address APIs require login.

```http
GET /api/addresses
POST /api/addresses
PUT /api/addresses/{id}
DELETE /api/addresses/{id}
PATCH /api/addresses/{id}/default
```

Create or update request:

```json
{
  "receiverName": "Zhang San",
  "receiverPhone": "13800000000",
  "province": "Guangdong",
  "city": "Shenzhen",
  "district": "Nanshan",
  "detailAddress": "No. 1 Supermarket Road",
  "isDefault": true
}
```

## Orders

### Create Order

```http
POST /api/orders
Authorization: Bearer <token>
```

Request:

```json
{
  "cartItemIds": [1, 2],
  "addressId": 1,
  "remark": "Please deliver after 18:00"
}
```

Important backend behavior:

- Validate cart ownership.
- Validate product status.
- Validate stock.
- Create order and order items.
- Deduct stock.
- Create stock logs.
- Remove submitted cart items.

### User Order List

```http
GET /api/orders?page=1&size=10&status=PAID
Authorization: Bearer <token>
```

### User Order Detail

```http
GET /api/orders/{id}
Authorization: Bearer <token>
```

### Cancel Order

```http
POST /api/orders/{id}/cancel
Authorization: Bearer <token>
```

Cancellation should return stock when the order has already deducted inventory and has not been completed.

### Mock Pay Order

```http
POST /api/orders/{id}/pay
Authorization: Bearer <token>
```

The MVP can use mock payment. It changes order status from `PENDING_PAYMENT` to `PAID`, writes a `payment_record`, and sets `paid_at`.

## Admin Orders

### Admin Order List

```http
GET /api/admin/orders?page=1&size=10&status=PAID&keyword=202608020001
Authorization: Bearer <admin-token>
```

### Admin Order Detail

```http
GET /api/admin/orders/{id}
Authorization: Bearer <admin-token>
```

### Admin Ship Order

```http
POST /api/admin/orders/{id}/ship
Authorization: Bearer <admin-token>
```

Request:

```json
{
  "remark": "Order shipped"
}
```

### Admin Complete Order

```http
POST /api/admin/orders/{id}/complete
Authorization: Bearer <admin-token>
```

## Admin Stock

### Stock Log List

```http
GET /api/admin/stock-logs?page=1&size=10&productId=1
Authorization: Bearer <admin-token>
```

### Adjust Stock

```http
POST /api/admin/products/{id}/stock-adjustments
Authorization: Bearer <admin-token>
```

Request:

```json
{
  "changeQuantity": 50,
  "bizType": "PURCHASE",
  "remark": "Purchase stock-in"
}
```

`changeQuantity` can be positive or negative. The backend must reject operations that make stock less than 0.

## Admin Users

### User List

```http
GET /api/admin/users?page=1&size=10&keyword=zhang
Authorization: Bearer <admin-token>
```

### Disable Or Enable User

```http
PATCH /api/admin/users/{id}/status
Authorization: Bearer <admin-token>
```

Request:

```json
{
  "status": 0
}
```

## MVP Implementation Order

1. Public category and product query.
2. User register and login.
3. Cart APIs.
4. Order creation and order query.
5. Admin product and order management.
6. Stock log and stock adjustment.

## P0 Additions

### Shipping

Admin ships a paid order with express information:

```http
POST /api/admin/orders/{id}/ship
```

Request:

```json
{
  "shipCompany": "顺丰速运",
  "shipNo": "SF1234567890"
}
```

### Order Lifecycle Extensions

User side:

```http
POST /api/orders/{id}/confirm-receipt
POST /api/orders/{id}/refund-apply
```

Refund apply request:

```json
{
  "reason": "商品破损，申请退款"
}
```

Admin side refund review:

```http
POST /api/admin/orders/{id}/refund-review
```

Request:

```json
{
  "approved": true,
  "remark": "已核实，同意退款"
}
```

Admin order query supports an extra filter:

```http
GET /api/admin/orders?refundStatus=APPLYING
```

Accepted refund statuses: `NONE`, `APPLYING`, `APPROVED`, `REJECTED`.

Result of an approved refund:

- Money returns to the buyer wallet.
- Payment record becomes `REFUNDED`.
- Order status becomes `CLOSED` and stock is returned.

### Automatic Timeout Close

A scheduled job closes `PENDING_PAYMENT` orders that stay unpaid longer than
`app.order.pay-timeout-minutes` (default 30 minutes). It returns stock, frees the
coupon used by the order, and sets the order status to `CLOSED`.

Configuration:

```yaml
app:
  order:
    pay-timeout-minutes: 30
    close-check-interval-ms: 60000
```

### Coupons

User APIs:

```http
GET  /api/coupons/available
POST /api/coupons/{id}/receive
GET  /api/coupons/mine
GET  /api/coupons/usable?amount=120.00
```

Use a coupon while creating an order:

```json
{
  "addressId": 1,
  "cartItemIds": [12, 13],
  "remark": "",
  "userCouponId": 5
}
```

Admin APIs:

```http
GET   /api/admin/coupons
POST  /api/admin/coupons
PATCH /api/admin/coupons/{id}/status
```

Coupon create request:

```json
{
  "name": "新用户满 50 减 10",
  "thresholdAmount": 50.00,
  "discountAmount": 10.00,
  "totalCount": 100,
  "startTime": "2026-08-28T00:00:00",
  "endTime": "2026-11-28T23:59:59"
}
```

### Product Reviews

```http
POST /api/reviews/orders/{orderId}
GET  /api/reviews/orders/{orderId}
GET  /api/reviews/products/{productId}?page=1&size=10
```

Review create request:

```json
{
  "rating": 5,
  "content": "新鲜，配送也快"
}
```

Rules:

- Only `COMPLETED` orders can be reviewed.
- One review is created for each order item of the order.
- Product review query is public.

### Stock Alerts

```http
GET /api/admin/stock-alerts
```

Returns products whose `stock` is less than or equal to their own
`low_stock_threshold`.

## Admin Product Listing & Sale Status

### Admin product list

```http
GET /api/admin/products?page=1&size=20&status=ON_SALE&keyword=apple
```

This endpoint intentionally ignores the sale status: it returns every product that
has not been deleted, so off-shelf products stay reachable and can be put back on
sale. Query parameters:

- `status` (optional) — one of `ON_SALE`, `OFF_SALE`, `DRAFT`
- `keyword` (optional) — matches product name or SKU, case insensitive

The public `GET /api/products` list only returns `ON_SALE` products, and the
public `GET /api/products/{id}` returns 404 once a product is taken off sale.

### Admin product detail

```http
GET /api/admin/products/{id}
```

Returns the product regardless of its sale status.

### Toggle sale status

```http
PATCH /api/admin/products/{id}/status
```

Request:

```json
{ "status": "OFF_SALE" }
```

Accepted values: `ON_SALE`, `OFF_SALE`, `DRAFT`. Taking a product off sale hides
it from the storefront immediately; existing orders are unaffected.
