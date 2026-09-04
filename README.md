# 超市购物系统 · Supermarket Shopping System

> 一个基于 **Spring Boot 3.5 + Vue 3** 的全栈超市在线购物平台，涵盖用户端购物流程与后台运营管理。
> *A full-stack online supermarket platform built with **Spring Boot 3.5 + Vue 3**, covering the end-user shopping flow and admin operations.*

## 目录 · Contents
- 功能特性 / Features
- 技术栈 / Tech Stack
- 目录结构 / Project Structure
- 快速开始 / Quick Start
- Docker Compose 部署 / Docker Compose
- 部署到 Railway / Deploy to Railway
- 文档 / Documentation
- 环境变量 / Environment Variables
- [English](#english)

---

## 功能特性 / Features

- **用户端**：注册/登录（JWT）、商品浏览与搜索、购物车、优惠券、下单、账户余额支付、发货、退款、评价、订单超时自动关单。
  *User side: signup/login (JWT), product browsing & search, cart, coupons, checkout, balance payment, shipping, refunds, reviews, and auto-close for timed-out orders.*
- **营销活动**：满减、折扣等多类活动，结账时自动选取**最优惠的单一活动**（不叠加）。
  *Promotions: full-reduction, discounts, etc.; checkout automatically picks the single best activity (no stacking).*
- **后台管理**：商品、分类、订单、用户、活动、库存预警等运营管理，封装在独立的 Admin 模块中。
  *Admin: manage products, categories, orders, users, promotions, and low-stock alerts in a standalone admin module.*
- **对象存储**：商品图片上传至阿里云 OSS。
  *Object storage: product images are uploaded to Alibaba Cloud OSS.*
- **缓存（可选）**：Redis 缓存商品详情 / 热门 / 新品，开关由 `APP_CACHE_ENABLED` 控制。
  *Caching (optional): Redis caches product detail / hot / new products, toggleable via `APP_CACHE_ENABLED`.*

---

## 技术栈 / Tech Stack

| 层 / Layer | 技术 / Technology | 说明 / Notes |
|---|---|---|
| 后端 / Backend | Spring Boot 3.5（parent 3.5.16）, Java 17 | REST API、JPA、Spring Security |
| 持久化 / Persistence | Spring Data JPA + MySQL 8 | 关系型存储 / Relational storage |
| 安全 / Security | Spring Security + JWT | 认证与鉴权 / AuthN & AuthZ |
| 缓存 / Cache | Spring Data Redis | 可选 / Optional |
| 存储 / Storage | 阿里云 OSS SDK 3.17.4 | 商品图片 / Product images |
| 前端 / Frontend | Vue 3 + Vite | 单页应用 / SPA |
| 路由与状态 / Routing & State | Vue Router 4 + Pinia | 路由与全局状态 / Routing & state |
| 可视化 / Charts | ECharts 6 | 数据看板 / Dashboards |
| 基础设施 / Infra | Docker Compose / Railway | 部署 / Deployment |
| 数据组件 / Data | MySQL 8, Redis 7 | 数据库与缓存 / DB & cache |

---

## 目录结构 / Project Structure

```
supermarket-system/
├── backend/            # Spring Boot 后端，监听 8080
├── frontend/           # Vue 3 前端（Vite 构建，nginx 托管）
├── deploy/
│   └── railway/        # Railway 部署配置、init.sql、命令清单
├── docs/               # API 设计、数据库设计文档
├── sql/                # 数据库脚本
├── docker-compose.yml  # 本地一键编排（mysql/redis/backend/frontend）
└── .mvn/               # Maven Wrapper
```

---

## 快速开始 / Quick Start

### 前置 / Prerequisites
- JDK 17、Node.js 18+、MySQL 8（本地开发）
  *JDK 17, Node.js 18+, MySQL 8 for local development.*
- （可选）Redis 7 用于缓存
  *Redis 7 optional for caching.*

### 后端 / Backend
```bash
cd backend
# 需本地 MySQL 已建库 supermarket_system，并导入 sql/supermarket.sql
mvn package -DskipTests
DB_USERNAME=root DB_PASSWORD=yourpassword java -jar target/*.jar --server.port=8080
```

### 前端 / Frontend
```bash
cd frontend
npm install
npm run dev      # 访问 http://localhost:5173
```
> 前端通过 `/api` 反代后端；开发时修改 `vite.config.js` 的 proxy 或环境变量 `VITE_API_BASE`。
> *The frontend calls the backend via the `/api` reverse proxy.*

---

## Docker Compose 部署 / Docker Compose

本地一键拉起全套（MySQL / Redis / 后端 / 前端 nginx）：
```bash
docker compose up -d
# 前端 http://localhost:80 ，后端 http://localhost:8080
```
> 首次启动需先导入数据库：用 `mysql` 客户端把 `sql/supermarket.sql`（或 `deploy/railway/init.sql`）导入 `supermarket_system` 库。
> *Import the database on first run.*

---

## 部署到 Railway / Deploy to Railway

详见 [`deploy/railway/README.md`](deploy/railway/README.md) 与命令清单 [`deploy/railway/COMMANDS.md`](deploy/railway/COMMANDS.md)。

要点：后端、前端各作为**独立服务**（Root Directory 分别为 `backend` / `frontend`），MySQL / Redis 使用 Railway 模板，前端通过 `BACKEND_URL` 把 `/api` 反代到后端，浏览器视角同源、免 CORS。
*Backend & frontend are separate services; MySQL/Redis via Railway templates; the frontend proxies `/api` to the backend via `BACKEND_URL` (same-origin, no CORS).*

---

## 文档 / Documentation

- [API 设计 / API Design](docs/api-design.md)
- [数据库设计 / Database Design](docs/database-design.md)
- [Railway 部署 / Railway Deploy](deploy/railway/README.md)

---

## 环境变量 / Environment Variables

后端支持通过环境变量覆盖数据库、Redis、JWT、OSS 等配置（见 `backend/src/main/resources/application.yml` 中的 `${VAR:default}` 写法）。
完整变量表与 Railway 服务间引用语法（`${{Service.VAR}}`）见 [`deploy/railway/README.md` 的「环境变量」章节](deploy/railway/README.md#环境变量)。

> **安全提示**：开发用真实 `application.yml`（含 OSS 密钥）已被 `.gitignore` 排除，不会进入仓库。生产部署请使用 `deploy/railway/application.yml.example` 并通过环境变量注入 OSS 凭证。
> *Security note: the dev `application.yml` containing OSS secrets is gitignored and never committed. For production, use `application.yml.example` and inject OSS credentials via environment variables.*

---

## License

MIT

---

# English

## Supermarket Shopping System
A full-stack online supermarket platform built with **Spring Boot 3.5 (Java 17)** and **Vue 3 + Vite**, providing an end-user shopping experience and an admin operations console.

### Features
- **User side**: signup/login (JWT), product browsing & search, cart, coupons, checkout, balance payment, shipping, refunds, reviews, and auto-close for timed-out orders.
- **Promotions**: full-reduction, discounts, etc.; checkout automatically picks the single best activity (no stacking).
- **Admin**: manage products, categories, orders, users, promotions, and low-stock alerts in a standalone admin module.
- **Object storage**: product images are uploaded to Alibaba Cloud OSS.
- **Caching (optional)**: Redis caches product detail / hot / new products, toggleable via `APP_CACHE_ENABLED`.

### Tech Stack
- **Backend**: Spring Boot 3.5 (parent 3.5.16), Java 17, Spring Data JPA + MySQL 8, Spring Security + JWT, Spring Data Redis, Alibaba Cloud OSS SDK 3.17.4.
- **Frontend**: Vue 3, Vite, Vue Router 4, Pinia, ECharts 6.
- **Infra**: Docker Compose / Railway, MySQL 8, Redis 7.

### Project Structure
```
backend/      Spring Boot API (port 8080)
frontend/     Vue 3 SPA (Vite build, nginx serve)
deploy/railway/   Railway config, init.sql, command cheat-sheet
docs/         API & database design docs
sql/          DB scripts
docker-compose.yml   local orchestration (mysql/redis/backend/frontend)
```

### Quick Start
```bash
# Backend — requires local MySQL with database `supermarket_system`
cd backend && mvn package -DskipTests
DB_USERNAME=root DB_PASSWORD=yourpassword java -jar target/*.jar --server.port=8080

# Frontend
cd frontend && npm install && npm run dev   # http://localhost:5173
```

### Deploy
- **Local**: `docker compose up -d`
- **Railway**: see [`deploy/railway/README.md`](deploy/railway/README.md) and [`deploy/railway/COMMANDS.md`](deploy/railway/COMMANDS.md).

### Documentation
- [API Design](docs/api-design.md) · [Database Design](docs/database-design.md)

### Environment Variables
All backend config is overridable via environment variables (see the `${VAR:default}` blocks in `application.yml`). The full variable table and Railway inter-service reference syntax (`${{Service.VAR}}`) are in [`deploy/railway/README.md`](deploy/railway/README.md#环境变量).

> **Security**: the dev `application.yml` containing OSS secrets is gitignored. For production use `deploy/railway/application.yml.example` and inject OSS credentials via env vars.

### License
MIT
