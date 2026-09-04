# 超市系统 · 服务器部署指南（手动发布）

目标：让别人通过 `http://服务器IP` 访问；平时你本地随便改，想发布再跑一次 `publish.sh`，
服务器跑的是「发布产物」（jar + dist），与本地开发目录完全隔离。

---

## 1. 买服务器（以阿里云 ECS 为例，与 OSS 同地域更省流量）
- **地域**：选 **华北2（北京）**，与你 OSS bucket（`oss-cn-beijing`）同区，OSS 内网流量免费、延迟低。
- **实例**：2 核 4G 起步（Spring Boot + MySQL 同机够用），突发/通用型均可。
- **镜像**：**Ubuntu 22.04 64 位**。
- **带宽**：按量或固定 3~5 Mbps 足够演示。
- **安全组（入方向）**：放行 `22`(SSH)、`80`(HTTP)、`443`(备用)；**3306 不要对公网开放**。
- 拿到公网 IP 和 root 密码。

## 2. 登录并做基础加固
```bash
ssh root@服务器IP
adduser deploy            # 建议建专用部署用户
usermod -aG sudo deploy
ufw allow 22,80,443
ufw enable
```
（进阶：禁止 root 直登、改 SSH 端口——可先跳过。）

## 3. 安装运行环境
```bash
sudo apt update
sudo apt install -y openjdk-17-jdk mysql-server nginx
java -version            # 确认 17
sudo mysql_secure_installation   # 设 root 密码
```
初始化数据库（把本仓库 `deploy/init.sql` 先传到服务器，例如 `scp deploy/init.sql root@IP:/tmp/`）：
```bash
# 先建库 + 建表 + 种子数据（init.sql 内含 CREATE DATABASE / USE / 建表 / 种子，含新人券 id=5）
sudo mysql < /tmp/init.sql

# 再建专用账号并授权（应用以此账号连库，不要用 root）
sudo mysql -e "CREATE USER IF NOT EXISTS 'supermarket'@'localhost' IDENTIFIED BY '你的强密码';"
sudo mysql -e "GRANT ALL PRIVILEGES ON supermarket_system.* TO 'supermarket'@'localhost';"
sudo mysql -e "FLUSH PRIVILEGES;"
```
> 注：应用 datasource 默认 `jdbc:mysql://localhost:3306/supermarket_system`，连接用户由
> `supermarket.service` 里的 `DB_USERNAME/DB_PASSWORD` 环境变量决定（覆盖 yml 默认的 root/root）。

## 4. 部署目录与运行用户
```bash
sudo useradd -r -s /bin/false supermarket
sudo mkdir -p /opt/supermarket /var/www/supermarket
sudo chown -R supermarket:supermarket /opt/supermarket
```

## 5. 配置 nginx（同一域名托管前端 + 反代 /api，同源无需 CORS）
```bash
sudo cp /tmp/nginx-site.conf /etc/nginx/sites-available/supermarket
sudo ln -sf /etc/nginx/sites-available/supermarket /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

## 6. 后端以系统服务运行
```bash
sudo cp /tmp/supermarket.service /etc/systemd/system/
# 重要：先编辑该文件，把 JWT_SECRET、DB_PASSWORD 的 CHANGE_ME 改成强值
sudo nano /etc/systemd/system/supermarket.service
sudo systemctl daemon-reload
sudo systemctl enable --now supermarket
sudo journalctl -u supermarket -f    # 看日志
```

## 7. 第一次发布（在本机 Git Bash 中）
先改 `deploy/publish.sh` 顶部的 `SERVER_HOST` 为你的服务器 IP，然后：
```bash
bash deploy/publish.sh
```
脚本会：本地构建 jar + 前端 dist → scp 到服务器 → 重启服务。
完成后浏览器打开 `http://服务器IP`，注册一个账号，验证能收到「新人专享券」。

---

## 日常开发 vs 服务器（你要的「平时改项目不让服务器跑」）
- **本地改代码**：本地 8080 / 5173 随便跑，不影响服务器上正在运行的发布版。
- **想让别人看到新版本**：本地自测 OK → 跑 `deploy/publish.sh` → 服务器更新为最新发布版。
- **回滚**：发布前 `ssh` 上去 `sudo cp /opt/supermarket/app.jar /opt/supermarket/app.jar.bak`；
  出问题把 `supermarket.service` 的 `ExecStart` 指回旧 jar 再 `systemctl restart supermarket`。

## 后续可加强（非必须）
- **HTTPS**：有域名并解析到该 IP 后，
  `sudo apt install certbot python3-certbot-nginx && sudo certbot --nginx` 一键免费证书；
  再把 `nginx-site.conf` 的 `server_name` 改成域名即可。
- **注册防刷**：公开后「注册自动发券」易被脚本刷，建议加频率限制 / 图形验证码（代码层）。
- **备份**：定期 `mysqldump supermarket_system > backup.sql`；OSS 图片在云端无需备份。
- 进程守护已由 systemd `Restart=on-failure` 兜底，崩溃会自动拉起。
