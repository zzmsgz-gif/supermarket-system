# Railway 部署命令清单（可复制）

> 配套文件：`README.md`（分步说明）、`.env.example`（环境变量）、`init.sql`（数据库）。
> 本清单所有命令均可直接复制粘贴执行。路径用 Git Bash / WSL 风格；Windows CMD/PowerShell 请自行改 `cd` 写法。
> **本机在跑的服务不受影响**——以下命令只用于把项目部署到 Railway 云端。

---

## 0. 前置：升级到 Hobby 档
Railway 控制台 → 账户 → 升级到 **Hobby（$5/月）**。Free 档每服务硬限 0.5GB 内存，跑不下 Spring Boot + MySQL + Redis。

---

## 1. 安装 Railway CLI

```bash
# macOS / Linux
brew install railway
# 或
npm install -g @railway/cli

# Windows (PowerShell)
npm install -g @railway/cli
```

验证：
```bash
railway --version
```

---

## 2. 登录
```bash
railway login
```
会自动打开浏览器完成授权。

---

## 3. 方式 A：GitHub 集成（✅ 推荐，最简单稳定）

### 3.1 把项目推到 GitHub
```bash
cd "D:/supermarket system"
# 若还没初始化 git
git init
git add .
git commit -m "init: supermarket system ready for deploy"
# 在 GitHub 新建仓库 supermarket-system，拿到地址后：
git remote add origin https://github.com/<你的用户名>/supermarket-system.git
git branch -M main
git push -u origin main
```

### 3.2 控制台操作（点几下即可）
1. Railway → **New Project** → **Deploy from GitHub repo** → 选 `supermarket-system`。
2. 画布上 **Create / New** 加 4 个服务：
   - **MySQL**：New → Deployment Service → 搜模板 `MySQL 8 or Any Version` → 设变量 `MYSQL_ROOT_PASSWORD`(强密码)、`MYSQL_DATABASE=supermarket_system`、`MYSQL_CONFIG=low`。
   - **backend**：New → Deployment Service → 选同一仓库 → **Settings → Root Directory = `backend`**（Dockerfile 自动被用上）。
   - **frontend**：New → Deployment Service → 选同一仓库 → **Settings → Root Directory = `frontend`**。
   - **Redis（可选）**：New → Empty Service → Image `redis:7-alpine`，设 `REDIS_PASSWORD`。
3. 每个服务的 **Variables** → **Raw Editor** → 把 `.env.example` 的内容粘进去（backend 改 `JWT_SECRET` 为强随机串，frontend 改 `BACKEND_URL` 为 backend 的 `*.up.railway.app` 域名）。
4. 各服务 **Settings → Networking → Generate Domain**（backend 和 frontend 都要生成）。

> GitHub 集成后，今后 `git push` 会自动重新部署对应服务。

---

## 4. 方式 B：纯 Railway CLI（适合不想用 GitHub）

> monorepo 多服务用 CLI 编排时，`railway up` 部署的是**当前目录**。需对每个子目录分别 link 到对应服务。

```bash
railway login

# ---- backend ----
cd "D:/supermarket system/backend"
railway init            # 跟随提示创建 Project
# 设定该服务的根目录（让 Railway 只在 backend/ 内构建）
railway environment edit --service-config backend source.rootDirectory /backend
railway up              # 上传并部署 backend

# ---- frontend ----
cd "D:/supermarket system/frontend"
railway environment edit --service-config frontend source.rootDirectory /frontend
railway up

# ---- MySQL 服务（控制台加模板最省事；CLI 也可：） ----
railway add -d mysql    # 注意：加的是官方 MySQL 模板，变量名以控制台为准
```

生成公开域名：
```bash
railway domain
```

> ⚠️ CLI 多服务首次部署建议先在控制台把 MySQL 模板加上并设好 `MYSQL_DATABASE` 等变量，再用 `railway up` 推 backend/frontend，否则 backend 启动会因连不上库而失败。

---

## 5. 生成强随机 JWT 密钥（替换 .env.example 里的占位值）

> **OSS 凭证提醒**：仓库里没有真实的 `application.yml`（`.gitignore` 已排除含密钥的本地版）。云端请使用 `deploy/railway/application.yml.example`（OSS 为占位符），并在 backend 变量里注入 `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` 等（见 `README.md` 变量表）。复制该 example 为 `backend/src/main/resources/application.yml` 即可参与构建。
```bash
# 任选其一
openssl rand -base64 48
# 或
python3 -c "import secrets; print(secrets.token_urlsafe(48))"
```
把输出填到 backend 变量的 `JWT_SECRET`。

---

## 6. 导入数据库（仅首次）
`ddl-auto: validate` 不会自动建表，必须先导入 `init.sql`（含 19 张表 + 种子数据）。

在 Railway **MySQL 服务 → Connect** 标签拿到 Public TCP 连接信息（host / port / user），本地执行：

```bash
# 若服务商未自动建库，先建
mysql -h <mysql-host> -P <mysql-port> -u <user> -p \
  -e "CREATE DATABASE IF NOT EXISTS supermarket_system CHARACTER SET utf8mb4;"

# 导入结构与数据
mysql -h <mysql-host> -P <mysql-port> -u <user> -p supermarket_system \
  < "D:/supermarket system/deploy/railway/init.sql"
```

> 若 MySQL 服务未开 Public Networking（TCP Proxy），需先在控制台开启，或用 Railway CLI 的 `railway run` / 临时开代理。

---

## 7. 验证部署
```bash
# 查看部署日志（排错连库/启动失败）
railway logs

# 查看当前链接状态
railway status

# 手动触发重新部署
railway redeploy
```

浏览器打开 frontend 的 `*.up.railway.app`：
- 商品列表能加载 → 后端 + MySQL 连通正常。
- 注册/登录/加购/下单走一遍 → 后端日志无 `Communications link failure`。

---

## 8. 常用 CLI 速查
| 命令 | 作用 |
|---|---|
| `railway login` | 浏览器登录 |
| `railway init` | 新建项目 |
| `railway link` | 本地目录链接到已有项目/服务 |
| `railway status` | 查看链接的项目/环境/服务 |
| `railway up` | 上传并部署当前目录 |
| `railway domain` | 生成公开域名 |
| `railway logs` | 查看实时日志 |
| `railway redeploy` | 重新部署 |
| `railway environment edit --service-config <svc> source.rootDirectory /<dir>` | 设服务根目录 |
| `railway run <cmd>` | 用云端变量在本地跑命令 |

---

## 9. 常见坑
- **backend 连不上 MySQL**：`SPRING_DATASOURCE_URL` 必须用 `${{MySQL.MYSQLHOST}}` 等 Railway 引用变量拼，写死 `localhost` 会指向 backend 自己。
- **变量名对不上**：服务间引用 `${{ServiceName.VAR}}`，`ServiceName` 是你在控制台给服务起的名字（如 `MySQL`），`VAR` 以该服务 Variables 列表里实际显示的为准。
- **前端白屏 / 接口 404**：`BACKEND_URL` 没填或填错；或 frontend 的 Root Directory 没设成 `frontend`。
- **首次启动报表不存在**：忘了第 6 步导入 `init.sql`。
- **内存不足被 kill**：backend 设 `JAVA_OPTS=-Xmx512m`，MySQL 设 `MYSQL_CONFIG=low`。
