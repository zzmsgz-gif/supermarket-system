# 部署到 Railway（Vue3 + Spring Boot + MySQL + Redis）

本目录包含把「超市购物系统」部署到 [Railway](https://railway.com) 所需的配置与初始化脚本。

> ⚠️ **计费现实**：Railway 的 Free 档（30 天试用后 $1/月）硬限**每服务 0.5GB 内存**，跑不下 Spring Boot + MySQL + Redis。请使用 **Hobby 档（$5/月，含 $5 额度）**。本全栈在 Railway 上的实际成本约 **$5–15/月**（内存 $10/GB·月、CPU $20/vCPU·月，低流量时 $5 额度基本覆盖）。比廉价 ECS 贵，但换来零运维（自动 HTTPS、重启、私有内网、备份）。

---

## 架构

```
用户浏览器
   │  https://<frontend>.up.railway.app
   ▼
frontend 服务 (nginx: 托管 Vue + 反代 /api)
   │  /api/*  →  BACKEND_URL (私有/公网)
   ▼
backend 服务 (Spring Boot :8080)
   ├──► MySQL 服务   (Railway 私有网络，utf8mb4)
   └──► Redis 服务   (可选；不开则 APP_CACHE_ENABLED=false)
阿里云 OSS (商品图片，已在 yml 字面量配置，跨云可访问)
```

前端用相对路径 `/api` 调接口，由 nginx 反代到后端，**浏览器视角同源，无需 CORS**。

---

## 前置条件

1. 一个 GitHub 仓库，含本项目的 `backend/` 与 `frontend/` 两个子目录。
2. Railway 账号（注册送 $5 试用额度），并升级到 **Hobby 档**（$5/月）。
3. 本地装有 `mysql` 客户端（用于首次导入数据）。

---

## 步骤

### 1. 新建 Project
Railway 控制台 → New Project → 选「Deploy from GitHub repo」，选定你的仓库。

### 2. 加 MySQL 服务
- New → Deployment Service → 搜模板 **「MySQL 8 or Any Version」**（feliperosenek/mysql-any-version-railway）。
- Variables 设：
  - `MYSQL_ROOT_PASSWORD` = 强随机密码（务必记录）
  - `MYSQL_DATABASE` = `supermarket_system`
  - `MYSQL_CONFIG` = `low`（压到 ~100–200MB 内存，省成本）
- 确认已挂 Volume（自动挂 `/var/lib/mysql`，数据持久化）。

### 3.（可选）加 Redis 服务
- New → Empty Service → Image：`redis:7-alpine`。
- 设 `REDIS_PASSWORD` = 强密码。
- 不开也能跑（缓存默认关闭）；开了就把 backend 的 `APP_CACHE_ENABLED` 设 `true`。

### 4. 加 backend 服务
- New → Deployment Service → 选同一仓库。
- **Root Directory** = `backend`（关键：构建上下文要在 backend 内）。
- Dockerfile 路径默认 `Dockerfile` 即可。
- Variables 见下方「环境变量」。

### 5. 加 frontend 服务
- New → Deployment Service → 选同一仓库。
- **Root Directory** = `frontend`。
- Dockerfile 默认 `Dockerfile`（已是多阶段：node 构建 → nginx 托管）。
- Variables：
  - `BACKEND_URL` = `https://<backend服务生成的域名>.up.railway.app`
  - （先建好 backend 拿到域名再回来填；域名为 `xxx.up.railway.app` 形式）

### 6. 导入数据库（仅首次）
等 MySQL 服务起来后，在 Railway 该服务的 Variables / TCP Proxy 拿到连接信息（host/port/用户 root/密码），在**本地**执行：

```bash
# 用 Railway 提供的 MySQL 公网（TCP Proxy）连接串
mysql -h <mysql-host> -P <mysql-port> -u root -p supermarket_system < deploy/railway/init.sql
```

> `init.sql` 由本机 `mysqldump supermarket_system` 生成，含 19 张表结构 + 种子数据（商品、活动、管理员、新人券等）。因 `ddl-auto: validate`，目标库必须已有精确表结构，故需先导入。

### 7. 验证
- 打开 frontend 域名，应能加载商品列表。
- 注册/登录、加购、下单走一遍；后端日志无 `Communications link failure`（连库失败）即可。

---

## 环境变量

### backend 服务
| 变量 | 值 / Railway 引用 | 说明 |
|---|---|---|
| `PORT` | `8080`（Railway 自动注入） | Spring 读 `${PORT:8080}` |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false` | 完整 JDBC 串，由 MySQL 服务变量拼出 |
| `DB_USERNAME` | `${{MySQL.MYSQLUSER}}` | |
| `DB_PASSWORD` | `${{MySQL.MYSQLPASSWORD}}` | |
| `JWT_SECRET` | 强随机串（≥32 字节） | **必须设**，否则用默认弱密钥 |
| `JWT_EXPIRATION_SECONDS` | `86400` | 可选 |
| `APP_CACHE_ENABLED` | `false`（或 `true`，需配 Redis） | |
| `SPRING_DATA_REDIS_HOST` | `${{Redis.REDIS_HOST}}` | 仅开缓存时设 |
| `SPRING_DATA_REDIS_PORT` | `${{Redis.REDIS_PORT}}` | 仅开缓存时设 |
| `SPRING_DATA_REDIS_PASSWORD` | `${{Redis.REDIS_PASSWORD}}` | 仅开缓存时设 |
| `APP_NEW_USER_COUPON_ID` | `5` | 新人券 id |
| `OSS_ENDPOINT` | `https://oss-cn-beijing.aliyuncs.com` | 用 `application.yml.example` 时需设 |
| `OSS_BUCKET` | `super-zzm` | 同上 |
| `OSS_ACCESS_KEY_ID` | 真实 AccessKeyId | 同上 |
| `OSS_ACCESS_KEY_SECRET` | 真实 AccessKeySecret | 同上 |
| `OSS_BASE_URL` | `https://super-zzm.oss-cn-beijing.aliyuncs.com` | 同上 |
| `JAVA_OPTS` | `-Xmx512m -XX:+UseG1GC -XX:MaxMetaspaceSize=128m` | 调堆，可选 |

> **关于 OSS 凭证（重要）**：本机开发用的真实 `application.yml`（含 OSS 字面量密钥）已被 `.gitignore` 排除，**不会进入仓库**。云端部署请改用本目录的 `application.yml.example`（其中 OSS 的 `access-key-id`/`secret` 已是 `${{...}}` 占位符），并通过以下环境变量注入真实凭证，再 `mvn package`：
> | 变量 | 示例值 |
> |---|---|
> | `OSS_ENDPOINT` | `https://oss-cn-beijing.aliyuncs.com` |
> | `OSS_BUCKET` | `super-zzm` |
> | `OSS_ACCESS_KEY_ID` | 真实 AccessKeyId |
> | `OSS_ACCESS_KEY_SECRET` | 真实 AccessKeySecret |
> | `OSS_BASE_URL` | `https://super-zzm.oss-cn-beijing.aliyuncs.com` |
>
> 用法：把 `application.yml.example` 复制为 `backend/src/main/resources/application.yml`（构建时打进 jar），或在 Railway Variables 里直接设上述变量（jar 内的 example 已用 `${{...}}` 读取）。

### frontend 服务
| 变量 | 值 | 说明 |
|---|---|---|
| `BACKEND_URL` | `https://<backend>.up.railway.app` | nginx 反代 `/api` 的目标 |

---

## 成本与调优提示
- 内存是主要成本：`backend` 限 `-Xmx512m`、`MySQL` 用 `MYSQL_CONFIG=low` 可把整体压到 ~1GB 内。
- 不常用的活动/优惠券查询若想加速，再加 Redis 服务并开 `APP_CACHE_ENABLED=true`（约多 $1–2/月）。
- 流量上来后按需升 Railway 服务内存档位，按用量计费。

## 常见坑
- **backend 连不上 MySQL**：多半是 `SPRING_DATASOURCE_URL` 没用 Railway 的 `${{MySQL.MYSQLHOST}}` 等变量拼，写死 `localhost` 会指向 backend 自己。
- **前端白屏/接口 404**：`BACKEND_URL` 没填或填错；或 frontend 服务 Root Directory 没设 `frontend` 导致 `COPY dist` 找不到构建产物。
- **首次启动报表不存在**：忘了执行第 6 步导入 `init.sql`（`validate` 模式不会自动建表）。
