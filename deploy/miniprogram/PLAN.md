# 微信小程序版本落地方案（规划）

> 状态：规划阶段，未开工。本文件不含任何密钥，AppID / 商户号等以占位符表示，真实值由用户提供后填入 `application.yml` 与 Railway 变量。
> 关联：H5 移动端适配（响应式补齐）已先行完成核心回归，见 git 提交 `fd2bd90`。

## 0. 结论

- **H5 响应式先上线**（已完成）：复用现有 Vue 工程，零后端改动，手机浏览器直接逛+买。
- **小程序独立立项**：后端当前**零微信能力**（仅 JWT 密码登录 + 钱包余额支付），前端 Vue 组件大量依赖浏览器 API，**无法直接编译成小程序**。需另起小程序工程 + 补后端微信登录/支付模块。

## 1. 现状盘点（基于代码实测）

**前端**
- Vue3 + Vite + vue-router + axios；`styles.css` 已有 `1180/900/760/720/560px` 多档断点。
- 移动端 H5 核心页（首页/详情/购物车/结算/收银台/订单）在 375px 下已验证**零横向溢出**。
- 但现有组件强依赖浏览器环境：`window` / `document` / `localStorage` / `vue-router`(history 模式) / 图片走 `/api/uploads/*` 绝对路径。这些在小程序里不可用，需重写或条件编译。

**后端**
- Spring Boot 3.5 + Spring Security；鉴权仅 `POST /auth/login`（用户名+密码 → JWT）。
- 支付仅 `BALANCE`（钱包余额），`OrderService` 内 `PAYMENT_CHANNEL_BALANCE` 是唯一渠道。
- 全仓库 grep：`openid` / 微信登录 / 微信支付 / 小程序 / 支付宝 —— **均不存在**。即「纯 JWT + 余额」模式。

## 2. 前端技术选型

| 方案 | 复用现有 Vue 组件 | 多端(微信/支付宝/H5) | 坑 | 建议 |
|---|---|---|---|---|
| 微信原生 (WXML/WXSS/JS) | 否，全重写 | 否 | 最少，最贴微信 | 仅微信小程序时首选 |
| uni-app (Vue 语法) | 部分（逻辑/composable 可复用，template 需改 uni 组件） | 是 | 条件编译、uni 组件差异 | 未来要多端时首选 |
| Taro (React 语法) | 否（Vue→React 改写） | 是 | 成本最高 | 不推荐（现有是 Vue） |

**推荐**：
- 只做微信小程序 → **微信原生** + 抽一层 `api`（用 `wx.request` 封装，复用后端接口契约）。
- 未来要支付宝/百度等多端 → **uni-app**（Vue 语法接近，迁移成本低于 Taro）。

## 3. 后端必须补的缺口

1. **微信小程序登录** `POST /auth/wechat-login` `{code}`
   - 调微信 `code2session(appid, secret, code)` 换 `openid`。
   - 查/建 `sys_user`（`wx_openid` 新列）→ 发 JWT（复用现有 `JwtAuthenticationFilter`）。
   - `sys_user` 加 `wx_openid` 列：`deploy/upgrade-*.sql` + `init.sql` 同步（ddl-auto=validate 约束）。
2. **微信支付 JSAPI**
   - 统一下单 → 返回 `prepay_id` → 前端 `wx.requestPayment` 调起。
   - 支付结果：微信推送 **`/pay/wechat/notify`**（需公网可访问）→ 验签+解密 → 复用现有 `payOrder` 逻辑置订单 `PAID`。
   - 该回调路径在 `SecurityConfig` **放行但必须验签**（不能裸 `permitAll`）。
3. **退款** `POST /orders/{id}/refund-apply` 对接微信退款 API + `/pay/wechat/refund-notify` 回调（现有申请流程补齐渠道）。
4. **支付渠道枚举**：`Order` 新增 `WECHAT` 渠道（现仅 `BALANCE`）；`OrderService` 下单/支付状态机扩展。
5. **配置**：`wx.appid` / `wx.secret` / `wx.pay.mch-id` / `wx.pay.api-key` / 证书路径 → `application.yml` + Railway 变量。OSS 已停用，小程序图片走后端 `/api/uploads` 或自有 CDN。

## 4. 复用与边界

- 商品 / 购物车 / 订单 / 营销 / 会员等**全部后端 API 直接复用**，小程序只换「JWT 怎么来」（从 wx.login code 换，而非密码）。
- 前端不共享组件，但可共享后端 DTO 字段约定（已在 `backend/src/main/java/.../dto`）。
- 运营后台（admin）不进小程序，维持 PC Web。

## 5. 排期建议

- **阶段 A（当前，已完成）**：H5 响应式上线。
- **阶段 B（小程序立项，需你提供素材）**：微信 AppID + 商户号 + 技术栈拍板 → 后端补登录/支付模块（约 1~2 周）→ 小程序前端 MVP（首页/商品/购物车/微信支付下单，约 2~3 周）。
- **阶段 C（可选）**：小程序端运营看板 / 客服消息。

## 6. 待你决策（开工前必给）

1. 小程序技术栈：**微信原生** 还是 **uni-app**？
2. 是否需要微信生态能力（拼团 / 分享裂变 / 公众号菜单引流）？
3. 提供 **微信 AppID** + **微信支付商户号** 后，我才开始后端模块（否则只能先写前端骨架）。
4. 小程序订单是否也走「建单即锁库存」同款逻辑（与 H5 一致，推荐保持一致）。
