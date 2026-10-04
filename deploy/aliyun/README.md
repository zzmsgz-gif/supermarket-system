# 超市系统 · 阿里云 ECS 部署（Alibaba Cloud Linux 3.2104）

目标机器：`8.218.154.150`（Alibaba Cloud Linux 3.2104 LTS 64 位，RHEL8 系）
架构：一台机跑全套 —— nginx 托管前端 `dist` + 反代 `/api` 到 Spring Boot :8080 + MySQL 8 同机。
**不用 OSS**：`application-prod.yml` 里 `storage.type: local` 已硬编码，上传文件落本机
`/opt/supermarket/uploads`，经 `/api/uploads/**` 由后端直接返回。

> 已有的 `deploy/server-setup.md` + `nginx-site.conf` + `supermarket.service` 是 **Ubuntu 版**，
> 本目录是按 Alibaba Cloud Linux 3 另写的一套（dnf / firewalld / SELinux / nginx conf.d 都不同），别混用。

---

## 0. 控制台先做两件事
1. **安全组（入方向）**：放行 `22`、`80`（443 备用）。**3306 绝不对公网开放**（要管库走 SSH）。
2. 确认地域：香港地域免备案，可随时绑域名；内地地域只能纯 IP 访问（试用机不支持备案）。

## 1. 把安装脚本和 SQL 传到服务器（本机 Git Bash）
```bash
cd "D:/supermarket system"
scp deploy/init.sql                 root@8.218.154.150:/tmp/init.sql
scp deploy/aliyun/01-setup-server.sh root@8.218.154.150:/tmp/
scp deploy/aliyun/supermarket.conf   root@8.218.154.150:/tmp/
scp deploy/aliyun/supermarket.service root@8.218.154.150:/tmp/
```

## 2. 服务器上执行初始化
```bash
ssh root@8.218.154.150
cd /tmp && bash 01-setup-server.sh
```
脚本会：装 JDK17/MySQL8/nginx → 导入 `init.sql`（建库建表 + 种子，utf8mb4）→ 建 `supermarket` 库账号
→ 建 `/opt/supermarket`、`/var/www/supermarket` → 配 nginx → 放行 firewalld/SELinux → 装 systemd 服务。

脚本结束会**打印数据库口令**，把它填进服务文件：
```bash
nano /etc/systemd/system/supermarket.service   # 改 DB_PASSWORD / JWT_SECRET / 管理员口令
systemctl daemon-reload
```
（JWT 随机串：`openssl rand -base64 48`）

## 3. 本机一键发布
```bash
bash deploy/aliyun/publish.sh
```
会本机构建 jar + dist → 上传 → 重启服务，并自动留存 `app.jar.bak` 便于回滚。

## 4. 验收
```bash
curl -I http://8.218.154.150/                  # 200
curl -s  http://8.218.154.150/api/products?size=1 | head -c 200   # 有 JSON
```
浏览器打开 `http://8.218.154.150`：注册账号（应收到新人券）→ 加购 → 下单 → 付款。
管理员：登录后进 `/admin`（口令重置见下节）。

## 本机实测记录（2026-10-02 首次部署时的实际情况）

| 项 | 预设 | 实测 | 处理 |
|---|---|---|---|
| 内存 | 4G | **1.9G（2核2G 规格）** | JVM `-Xmx512m` + 加 1G swap 兜底 |
| SELinux | Enforcing（需放行反代） | **Disabled** | 无需 setsebool / restorecon |
| firewalld | 需放行 80 | **未运行** | 刻意不启用（只放 http/https 不加 ssh 会把自己锁在门外），入口交给安全组 |
| nginx | 直接加 conf.d 即可 | RHEL `nginx.conf` 自带 `server_name _` 默认块，会**抢走请求** | 已把默认块注释（备份 `nginx.conf.bak`），只留 conf.d/supermarket.conf |
| MySQL | — | 默认监听 `*:3306`（所有网卡） | 已加 `bind-address=127.0.0.1` 只听本机 |
| MySQL 授权 | 只建 `'user'@'localhost'` 即可 | 加了 `skip-name-resolve` 后 **127.0.0.1 不再解析成 localhost**，JDBC 连不上（`/api` 全 500） | 脚本已改为同时授权 `localhost` / `127.0.0.1` / `%`，并加连库自校验 |
| admin 口令 | 用 `APP_BOOTSTRAP_ADMIN_*` | 种子自带 admin（占位哈希，明文无人知），引导逻辑**不覆盖已有账号** | 用 bcrypt 生成哈希写库（见下节） |

## 首次部署后必做：重置 admin 口令

`deploy/init.sql` 种子里的 `admin`（id=1）用的是**写死的占位哈希**，明文无人知晓；
`supermarket.service` 的 `APP_BOOTSTRAP_ADMIN_*` 只在账号不存在时创建，**不会覆盖已有账号**。
所以要自己生成一个 bcrypt 哈希写库：

```bash
# 本机（Python）：生成哈希（Spring Security 认 $2a/$2b 前缀）
python -c "import bcrypt;print(bcrypt.hashpw(b'你的口令', bcrypt.gensalt(rounds=10)).decode())"
```
```sql
-- 写成 .sql 文件再导入。⚠️ 千万别在 ssh 里写 mysql -e "UPDATE ... '$2a$...'"
-- 哈希里的 $ 会被 shell 当变量吃掉（首版实测口令被存成了 'a0...'）
USE supermarket_system;
UPDATE sys_user SET password_hash='<上面生成的哈希>', role='ADMIN', status=1 WHERE username='admin';
```

## 常见问题
| 现象 | 排查 |
|---|---|
| 访问 `/api/*` 502 | SELinux 拦了反代：`setsebool -P httpd_can_network_connect 1` |
| 页面白屏 / 403 | 静态目录 SELinux 标签：`semanage fcontext -a -t httpd_sys_content_t "/var/www/supermarket(/.*)?" && restorecon -Rv /var/www/supermarket` |
| 后端起不来（表不存在） | `ddl-auto` 默认 `update` 会自动建表；确认 `init.sql` 导入成功 |
| 中文乱码 / ERROR 1366 | mysql 客户端务必带 `--default-character-set=utf8mb4` |
| 发布后页面没变 | nginx 缓存了 `/assets`：清浏览器缓存，或 `systemctl reload nginx` |

## 日常
- 本地改代码 → 本地 8080/5173 自测 → OK 了再跑 `publish.sh`，服务器才更新。
- 回滚：`ssh` 上去 `cp /opt/supermarket/app.jar.bak /opt/supermarket/app.jar && systemctl restart supermarket`。
- 备份：`mysqldump supermarket_system > /root/backup-$(date +%F).sql`（试用机 3 个月到期会释放，记得提前导出）。
