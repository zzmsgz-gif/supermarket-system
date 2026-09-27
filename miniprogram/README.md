# 超市购物 · 微信小程序（uni-app）

> 技术栈：**uni-app + Vue3 + Vite**，首期仅微信小程序。后端复用现有 Spring Boot REST API，只换「JWT 怎么来」。
> 关联规划：`../deploy/miniprogram/PLAN.md`

## 范围（本期）

- ✅ 前端骨架：首页 / 商品详情 / 购物车 / 结算 / 订单 / 微信登录页
- ✅ 微信登录：`uni.login` 拿 code → 调后端 `/auth/wechat-login` 换 JWT（**后端已实现**）
- ✅ 支付：走现有 **钱包余额 BALANCE** 渠道（无商户号，不做真实微信支付）
- ⏸ 真实微信支付（JSAPI / `/pay/wechat/notify` / 退款）：拿到商户号后再补

## 目录结构

```
miniprogram/
├── package.json            # 依赖与脚本
├── vite.config.js
├── index.html              # H5 构建入口
└── src/
    ├── main.js
    ├── App.vue
    ├── pages.json          # 页面路由 + tabBar
    ├── manifest.json       # mp-weixin.appid 已填 wx3a6edd169faeba2e
    ├── config.js           # API_BASE / APP_ID / fullUrl 图片拼接
    ├── api/                # 接口封装（request + auth/product/cart/order）
    ├── store/auth.js       # 登录态本地存储（token + user）
    └── pages/              # login / index / product / cart / checkout / orders / order-detail
```

## 运行

```bash
cd miniprogram
npm install                 # 依赖装在本地（全局 npm 不可用，装到本项目）
npm run dev:mp-weixin        # 开发者工具打开 dist/dev/mp-weixin
```

- 微信开发者工具导入 `miniprogram/` 目录（或构建产物 `dist/dev/mp-weixin`）。
- `manifest.json` 里 `mp-weixin.appid` 已填 `wx3a6edd169faeba2e`；若你换号，改这里。
- **不校验合法域名**：开发者工具「详情 → 本地设置」勾选，否则 http 后端调不通。
- 后端 `src/config.js` 的 `API_BASE` 改成后端可达地址（同机用 `http://localhost:8080`，真机用局域网 IP）。

## 后端对接状态（微信登录已打通）

后端已实现 `POST /auth/wechat-login {code}`：
1. `WechatService.code2Session(appid, secret, code)` 换 openid（secret 走 `@Value` 注入，不写死在代码）；
2. 查/建 `sys_user`（`wx_openid` 列，已同步 `init.sql` / `railway/init.sql` / `upgrade-wechat-login-20260925.sql`）；
3. 复用现有 `JwtService.generateToken` 发 JWT（与 PC 密码登录同款 token）。

该接口在 `SecurityConfig` 已 `permitAll`，小程序端调通即登录成功。

## API 契约（已按后端 DTO 对齐，2026-09-27 实测）

> 全部接口已对照 `backend/.../controller/*` 与 `.../dto/*` 真实实现核对，下列字段为本仓库当前真实返回。

- **商品详情**：`GET /products/{id}` → `ProductDetailResponse`（继承 `ProductSummaryResponse`）
  `{ id, name, coverUrl, price, originalPrice, description, skus:[{ specJson, price, originalPrice, image, sortNo }] }`
  多规格：详情页解析每个 SKU 的 `specJson` 出维度/可选项；加购时把 `skuSpec` 拼成 `属性名:值`（空格连接、键按字母序排序），如 `颜色:红色 尺码:XL`（与 PC 端同口径）。
- **购物车**：`GET /cart` → `CartResponse { items: List<CartItemResponse>, selectedCount, selectedAmount, activityDiscount, activityName }`
  行项为**扁平字段**：`id, productId, productName, productCoverUrl, skuSpec, productPrice, productOriginalPrice, quantity, subtotalAmount, flashSaleId, onSale …`（`subtotalAmount` 已是精确小计，前端直接求和）。
  - 加购：`POST /cart/items { productId, quantity, skuSpec? }`（注意是 `/items` 子路径）
  - 改数量：`PUT /cart/items/{id}` `{ quantity }`；删除：`DELETE /cart/items/{id}`
- **下单**：`POST /orders` → `OrderResponse`
  body：`{ cartItemIds: List<Long>, fulfillmentType: 'PICKUP', pickupStoreId }`（首期仅门店自提，无需收货地址）。
  返回：`{ id, orderNo, totalAmount, discountAmount, freightAmount, payAmount, status, fulfillmentType, pickupStoreId, pickupStoreName, pickupCode, items:[{ productName, skuSpec, productPrice, quantity, subtotalAmount }], payDeadline }`
- **支付**：`POST /orders/{id}/pay`（**无 body**，后端默认走钱包余额 `BALANCE`）。`PayPage` 倒计时用后端绝对 `payDeadline`。
- **门店（自提）**：`GET /stores`（permitAll）→ `List<StoreResponse>` `{ id, name, address, phone, businessHours, … }`，结算页选自提门店。
- **订单列表**：`GET /orders` → `PageResponse<OrderResponse> { content: [...] }`（取 `res.content` 渲染）。
- **订单详情**：`GET /orders/{id}` → `OrderResponse`（同上字段）。
- **登录**：`POST /auth/wechat-login { code }` → `AuthResponse { token, user }`（已实现）。

字段以 `backend/src/main/java/.../dto` 下真实类为准；若后端改 DTO，同步改 `src/api/*` 即可。

## 对 PC 端的影响

零。本目录完全独立，不触碰 `frontend/`（Vue Web）与 `backend/`（仅后续增量加 wechat-login）。
