# 超市购物系统 · Supermarket Shopping System

> 一个基于 **Spring Boot 3.5 + Vue 3** 的全栈超市在线购物平台，涵盖用户端购物流程与后台运营管理。
> *A full-stack online supermarket platform built with **Spring Boot 3.5 + Vue 3**, covering the end-user shopping flow and admin operations.*

线上演示 / Live: **https://haomart.xyz**

## 目录 · Contents
- 功能特性 / Features
- 技术栈 / Tech Stack
- 目录结构 / Project Structure
- 快速开始 / Quick Start
- 生产部署 / Production Deploy
- 接口限流 / API Rate Limiting
- 文档 / Documentation
- 环境变量 / Environment Variables
- [English](#english)

---

## 功能特性 / Features

### 用户端 / User side
- 注册登录（JWT）、微信登录、商品浏览与搜索、分类导航
- 购物车、优惠券（下单自动选最优单张）、下单、余额支付、充值
- 订单超时自动关单、发货、确认收货、**退款申请**、评价与回复
- 会员等级体系（7 档：普通 / 银卡 / 金卡 / 钻石 / 紫钻 / 黑卡 / 至尊），等级决定会员价与折扣率
- 收藏与降价提醒、消息中心
- 门店自提 + 配送时段选择（快递 / 自提双履约）

*Signup/login (JWT) & WeChat login, browsing & search, category nav, cart, coupons (best single one auto-applied), checkout, balance payment, recharge, auto-close for timed-out orders, shipping, confirm receipt, refund application, reviews with merchant replies, a 7-tier membership programme driving member pricing, favourites with price-drop alerts, message centre, and dual fulfilment (courier or store pickup with time slots).*

### 营销与运营 / Marketing & operations
- 营销活动：满减、折扣等多类，**结账时自动选取最优惠的单一活动（不叠加）**
- 限时秒杀：独立秒杀商品、限购名额、防超卖
- 会员日：日历式配置专属日
- 经营数据看板：销售额、订单量、库存水位、商品/会员排行

*Promotions (full-reduction, discounts) with **the single best activity auto-selected at checkout, never stacked**; flash sales as dedicated products with per-user limits and oversell protection; member-day calendar; and a dashboard covering sales, orders, stock levels and product/member rankings.*

### 后台管理 / Admin
18 个独立模块，懒加载按需加载：
`insights` 经营看板 · `orders` 订单 · `refunds` 售后 · `stock` 库存 · `reviews` 评价
· `products` 商品 · `categories` 分类 · `coupons` 优惠券 · `activities` 活动
· `flashSales` 秒杀 · `notices` 公告 · `hotSearches` 热搜词 · `memberDays` 会员日
· `banners` 轮播 · `stores` 门店 · `users` 用户 · `passwordResets` 密码重置申请

*18 lazily loaded modules covering dashboards, orders, after-sales, stock, reviews, products, categories, coupons, promotions, flash sales, announcements, hot searches, member days, banners, stores, users, and password-reset requests.*

### 工程 / Engineering
- 后端单模块（`backend/`），前端 SPA（`frontend/`）
- 前端逻辑按域拆成 **36 个 composable** + **18 个后台面板组件**，页面只做组装
- 启动时数据自愈（`DataSeeder`）：种子数据幂等导入 + 图片路径后缀自动修正
- 一切图片加载失败都有兜底（首字占位图），不会出现碎图
- 前端产物按内容 hash 分块（`vendor` 单块，避免跨 chunk 循环依赖）

*Domain-split composables and lazily loaded admin panels; idempotent seed data with startup self-repair; every `<img>` has a fallback; content-hashed chunks with a single vendor chunk to avoid cross-chunk cycles.*

---

## 技术栈 / Tech Stack

| 层 / Layer | 技术 / Technology | 说明 / Notes |
|---|---|---|
| 后端 / Backend | Spring Boot 3.5.16, Java 17 | REST API (8080) |
| 持久化 / Persistence | Spring Data JPA + MySQL 8 | 关系型存储 / Relational |
| 安全 / Security | Spring Security + JWT | 认证与鉴权 / AuthN & AuthZ |
| 缓存 / Cache | Spring Data Redis | 可选，构造器注入 / Optional, constructor-injected |
| API 文档 / API Docs | springdoc-openapi (Swagger UI) | 接口文档 |
| 前端 / Frontend | Vue 3 + Vite 5 | 单页应用 / SPA |
| 路由与状态 / Routing & State | Vue Router 4 + Pinia 3 | 路由与全局状态 / Routing & state |
| 可视化 / Charts | ECharts 6 | 经营看板 / Dashboards |
| 存储 / Storage | 本地磁盘（`/api/uploads/**`） | 抽象为 `FileStorageService`，可切 OSS / Local disk behind an interface |
| 部署 / Deploy | nginx + systemd（单机全套） | 一台机：nginx + JVM + MySQL |

> **为什么不用 OSS** / **Why not OSS**：生产用本地磁盘落图（`storage.local.dir`），经后端
> `/api/uploads/**` 直接返回，省一份对象存储费用。`OssFileStorageService` 仍在代码里，
> 切换只需改 `storage.type`。
> *Production stores uploads on local disk and serves them through the backend — no object
> storage bill. The OSS implementation is still present; switching is a config flag.*

---

## 目录结构 / Project Structure

```
supermarket-system/
├── backend/                     # Spring Boot 后端，监听 8080
│   └── src/main/
│       ├── java/…/config/DataSeeder.java   # 种子导入 + 启动自愈
│       └── resources/
│           ├── db/seed-data.sql            # 权威种子数据
│           ├── application-prod.yml        # 生产配置模板
│           └── application.yml             # 本地配置（gitignore，不入库）
├── frontend/                    # Vue 3 SPA（Vite 构建，nginx 托管）
│   └── src/
│       ├── components/          # 18 个后台面板 + 9 个通用组件
│       ├── composables/         # 36 个按域拆分的 composable
│       ├── pages/               # 15 个页面
│       ├── stores/              # Pinia：cart / user
│       └── api/client.js        # axios 实例 + 统一错误处理
├── miniprogram/                 # 微信小程序骨架
├── deploy/
│   ├── aliyun/                  # ★ 生产部署：安装脚本、nginx conf、限流、systemd
│   │   └── README.md            # 阿里云 ECS 部署详解（先看这个）
│   ├── init.sql                 # 新机建库脚本（无 DROP TABLE）
│   ├── upgrade-*.sql            # 存量库增量迁移，按日期命名
│   ├── verify-*.py              # 39 个端到端验证脚本（自带清理）
│   └── railway/                 # ⚠️ 历史方案，已废弃（仅存 application.yml.example 等参考）
├── docs/                        # API 设计、数据库设计
├── sql/                         # 数据库脚本
└── docker-compose.yml           # 本地一键编排（mysql/redis/backend/frontend）
```

---

## 快速开始 / Quick Start

### 前置 / Prerequisites
- JDK 17、Node.js 18+、MySQL 8（本地开发）
  *JDK 17, Node.js 18+, MySQL 8 for local development.*
- （可选）Redis 7 用于缓存
  *Redis 7 optional for caching.*

### 建库 / Create the database
> 用 `deploy/init.sql`。**它不含 `DROP TABLE`** —— 剥掉了那十几条会清空目标库的语句，
> 加载到任何库都是纯建表，误跑不会毁数据。
> *`deploy/init.sql` has no `DROP TABLE`; loading it is safe.*

```bash
mysql -u root -p --default-character-set=utf8mb4 supermarket_system < deploy/init.sql
```

> `--default-character-set=utf8mb4` 必须是 utf8mb4，否则中文入库报 `ERROR 1366`。
> *Required, or Chinese text fails with `ERROR 1366`.*

### 后端 / Backend
```bash
cd backend
mvn package -DskipTests
DB_USERNAME=root DB_PASSWORD=yourpassword java -jar target/*.jar --server.port=8080
```
> 首次启动会自动跑 `DataSeeder`（`db/seed-data.sql`）：插入缺失行、用权威种子值自愈已有行。
> 种子里的 `ON DUPLICATE KEY UPDATE` **刻意不含运营可改字段**（如公告、商品图、分类图标），
> 所以后台改动不会被重启冲掉。
> *On first start `DataSeeder` imports and self-heals. The seed's `ON DUPLICATE KEY UPDATE`
> deliberately omits operator-editable columns, so backend edits survive restarts.*

### 前端 / Frontend
```bash
cd frontend
npm install
npm run dev      # 访问 http://localhost:5173
```
> 前端通过 `/api` 反代后端；开发时改 `vite.config.js` 的 proxy 或环境变量 `VITE_API_BASE`。
> *The frontend calls the backend via the `/api` reverse proxy.*

> ⚠️ 别用 `npm run build` 往默认 `dist/` 写（可能被 Defender 独占锁住导致 `EPERM`）。
> 发布产物用 `npx vite build --outDir dist_deploy --emptyOutDir`。
> *Don't build into the default `dist/`; use a separate outDir.*

### 验证脚本 / Verification scripts
`deploy/verify-*.py` 有 39 个端到端脚本（下单、秒杀、会员价、履约、退款、评价…），
走真实 HTTP 接口，**自带清理**，跑完库内零残留：
```bash
cd deploy && python verify-cart-checkout.py
```

### Docker Compose
```bash
docker compose up -d     # 前端 :80，后端 :8080
```

---

## 生产部署 / Production Deploy

**当前生产：阿里云 ECS 单机全套**（香港地域，`8.218.154.150`）
一台机器同时跑 nginx（托管前端 + HTTPS + 反代 `/api`）+ Spring Boot（systemd）+ MySQL 8。

> **3306 绝不对公网开放**，要管库走 SSH。
> *Port 3306 is never exposed publicly; use SSH tunnels.*

完整步骤见 **[`deploy/aliyun/README.md`](deploy/aliyun/README.md)**，含安全组、dnf 安装、
firewalld、SELinux、nginx conf.d、systemd、SSL 证书签发。

发布流程：
```bash
# 1. 构建（后端 / 前端）
cd backend && mvn package -DskipTests
cd ../frontend && npx vite build --outDir dist_deploy --emptyOutDir

# 2. 备份生产（静态目录 + jar 都要备，jar 才是后端回滚的关键）
ssh root@HOST 'TS=$(date +%Y%m%d_%H%M%S)
  cp -a /var/www/supermarket /var/www/supermarket_bak_$TS
  cp -a /opt/supermarket/app.jar /opt/supermarket/app.jar.bak_$TS'

# 3. 上传 + 重启
scp -r dist_deploy/assets dist_deploy/index.html root@HOST:/var/www/supermarket/
scp backend/target/*.jar root@HOST:/opt/supermarket/app.jar
ssh root@HOST 'systemctl restart supermarket.service'
```

> ⚠️ **清理旧资源要以本地 `dist_deploy/assets/` 的文件清单为准**，
> **绝不能按 `index.html` 反推** —— 懒加载 chunk 由 JS 动态 `import()`，index.html 里根本没引用，
> 按 index.html 清理会误删它们（曾因此让 3 个页面 404）。
>
> ⚠️ `scp -r dist/*` 传**子目录**时要再带一次 `-r`（如 `scp -r dist_deploy/seed-products ...`），
> 否则该目录直接 `failed to upload`。
>
> ⚠️ `scp` 的退出码不可信：传了不存在的文件会整批中止，但可能已经传了一半。**上传后一律核对内容/mtime。**

> `deploy/railway/` 是更早的 Railway 方案，**已废弃**（最后改动是剥掉 `init.sql` 里的 `DROP TABLE`），仅留作存档与配置模板参考。当前生产路径只有上面这一条。

---

## 接口限流 / API Rate Limiting

nginx 层按 IP 限流（`deploy/aliyun/rate-limit.conf`）：
```
limit_req_zone $binary_remote_addr zone=api_perip:10m rate=30r/s;
# location /api: limit_req zone=api_perip burst=60 nodelay;
```

> **别调回 `10r/s + burst=20`**：SPA 是**并发**拉数据的，后台首屏一次发 33 个请求
> （订单、用户、商品、库存、活动、券、热搜、轮播、看板…），burst=20 会把其中 9 个打成 429，
> 表现为前端 `JSON.parse` 报 `Unexpected token '<'`、列表悄悄变空，看起来像「后台坏了」。
> 30r/s + burst=60 能容下一次完整首屏，同时仍挡住脚本洪泛。
>
> *A SPA loads its dashboard concurrently (33 requests on the admin first paint).
> 30r/s + burst=60 accommodates that; 10r/s + burst=20 does not.*

---

## 文档 / Documentation

- [API 设计 / API Design](docs/api-design.md)
- [数据库设计 / Database Design](docs/database-design.md)
- [阿里云部署详解 / Alibaba Cloud Deploy](deploy/aliyun/README.md)
- Swagger UI：后端启动后访问 `/swagger-ui.html`

---

## 环境变量 / Environment Variables

后端全部配置可用环境变量覆盖（`${VAR:default}` 写法），例如：

| 变量 / Variable | 用途 / Purpose |
|---|---|
| `DB_USERNAME` / `DB_PASSWORD` / `DB_URL` | 数据库 / Database |
| `JWT_SECRET` | 令牌签名密钥 / Token signing key |
| `STORAGE_LOCAL_DIR` | 上传目录 / Upload directory |
| `APP_CACHE_ENABLED` | 是否启用 Redis 缓存 / Toggle Redis cache |
| `OSS_*` | 切到 OSS 时用 / Only when `storage.type=oss` |

> **安全提示**：含真实凭证的 `application.yml` 已被 `.gitignore` 排除。
> 生产用 `application-prod.yml` + 环境变量注入，配置模板见 `deploy/railway/application.yml.example`，**任何密钥都不要提交进仓库**。
> *The real `application.yml` is gitignored. Never commit credentials — this repo is public.*

---

## License

MIT

---

# English

## Supermarket Shopping System

A full-stack online supermarket platform built with **Spring Boot 3.5.16 (Java 17)** and **Vue 3 + Vite**, providing an end-user shopping experience and an 18-module admin console.

Live: **https://haomart.xyz**

### Features

**User side** — signup/login (JWT) & WeChat login, browsing, search and category nav; cart, coupons (best single one auto-applied), checkout, balance payment, recharge; auto-close for timed-out orders, shipping, confirm receipt, refund applications, reviews with merchant replies; a **7-tier membership programme** driving member pricing; favourites with price-drop alerts; message centre; and dual fulfilment (courier or store pickup with time slots).

**Marketing & operations** — promotions (full-reduction, discounts) where **the single best activity is auto-selected at checkout, never stacked**; flash sales as dedicated products with per-user limits and oversell protection; a member-day calendar; and a dashboard covering sales, orders, stock levels and product/member rankings.

**Admin** — 18 lazily loaded modules: dashboards, orders, after-sales, stock, reviews, products, categories, coupons, promotions, flash sales, announcements, hot searches, member days, banners, stores, users, password-reset requests.

**Engineering** — 36 domain-split composables and 18 admin panel components; idempotent seed data with startup self-repair; an `imgFallback` on every image so nothing ever renders as a broken icon; content-hashed chunks with a single vendor chunk (a split vendor caused a cross-chunk cycle).

### Tech Stack
- **Backend**: Spring Boot 3.5.16, Java 17, Spring Data JPA + MySQL 8, Spring Security + JWT, springdoc-openapi, optional Spring Data Redis.
- **Frontend**: Vue 3, Vite 5, Vue Router 4, Pinia 3, ECharts 6.
- **Storage**: local disk behind a `FileStorageService` interface (an OSS implementation ships too — switch with one config flag).
- **Deploy**: nginx + systemd on a single Alibaba Cloud ECS instance, serving the SPA with HTTPS and reverse-proxying `/api` to the JVM; MySQL on the same host, port 3306 never exposed publicly.

### Project Structure
```
backend/          Spring Boot API (8080) + DataSeeder (idempotent seed + self-repair)
frontend/src/     18 admin panels, 36 composables, 15 pages, Pinia stores
miniprogram/      WeChat mini-program skeleton
deploy/aliyun/    ★ production deploy: setup script, nginx conf, rate limit, systemd
deploy/init.sql   schema (no DROP TABLE — safe to run against any database)
deploy/verify-*.py  39 end-to-end API verification scripts, self-cleaning
deploy/railway/   ⚠️ deprecated, kept for reference
```

### Quick Start
```bash
# 1. create the database (utf8mb4 required or Chinese text fails with ERROR 1366)
mysql -u root -p --default-character-set=utf8mb4 supermarket_system < deploy/init.sql

# 2. backend — on first start DataSeeder imports and self-heals
cd backend && mvn package -DskipTests
DB_USERNAME=root DB_PASSWORD=yourpassword java -jar target/*.jar --server.port=8080

# 3. frontend
cd frontend && npm install && npm run dev      # http://localhost:5173
```

> Don't build into the default `dist/` — publish with `npx vite build --outDir dist_deploy --emptyOutDir`.

Or run the whole stack locally: `docker compose up -d`.

### Production Deploy
Single Alibaba Cloud ECS host: nginx (SPA + HTTPS + `/api` reverse proxy) + Spring Boot (systemd) + MySQL 8. Full walkthrough in [`deploy/aliyun/README.md`](deploy/aliyun/README.md).

**Always back up both the static directory and `app.jar` before publishing**, and prune stale
assets using the **local `dist_deploy/assets/` listing** — never by reading `index.html`, which
omits every lazily imported chunk.

### API Rate Limiting
```
limit_req_zone $binary_remote_addr zone=api_perip:10m rate=30r/s;   # burst=60 nodelay
```
The admin dashboard issues **33 concurrent requests** on first paint; a tighter limit returns 429s
that surface as `JSON.parse` errors and silently empty tables.

### Documentation
[API Design](docs/api-design.md) · [Database Design](docs/database-design.md) · [Alibaba Cloud Deploy](deploy/aliyun/README.md) · Swagger UI at `/swagger-ui.html`

### Security
The real `application.yml` is gitignored. Inject credentials via environment variables —
**never commit them**; this repository is public.

### License
MIT
