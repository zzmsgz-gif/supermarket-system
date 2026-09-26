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

## API 契约假设（需按后端 DTO 对齐）

- 商品列表：`GET /products` → 分页 `{ content: [...] }`（也兼容裸数组）
- 购物车：`GET /cart` → `[{ id, product:{id,name,price,coverUrl}, quantity }]`
- 下单：`POST /orders` `{ items:[{productId,quantity}], channel:'BALANCE' }` → `{ id, ... }`
- 支付：`POST /orders/{id}/pay` `{ channel:'BALANCE' }`
- 登录返回：`{ token, user }`（后端 `AuthResponse`）

字段名以 `backend/src/main/java/.../dto` 下真实类为准，若有出入改 `src/api/*` 即可。

## 对 PC 端的影响

零。本目录完全独立，不触碰 `frontend/`（Vue Web）与 `backend/`（仅后续增量加 wechat-login）。
