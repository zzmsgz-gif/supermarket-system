# syntax=docker/dockerfile:1
# ============================================================
# 超市系统后端镜像（多阶段构建）
#   - 构建期：Maven 打出可执行 jar
#   - 本地含真实密钥的 application.yml 已被 .dockerignore 排除，绝不会进镜像
#   - 构建期写入「云端安全版」application.yml（全占位符，靠 Railway 变量注入密钥）
#   - DB 密码不设默认值：云端必须注入 DB_PASSWORD，否则启动即报错（不回退到本机口令）
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
# 预拉取依赖以利用层缓存（网络失败不阻断构建）
RUN mvn -q -B dependency:go-offline || true
COPY src ./src

# 写入云端安全版 application.yml（无真实密钥）
RUN cat > src/main/resources/application.yml <<'YML'
server:
  port: ${PORT:8080}
  servlet:
    context-path: /api
spring:
  application:
    name: supermarket-backend
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:3306/supermarket_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    show-sql: false
    properties:
      hibernate:
        format_sql: true
  servlet:
    multipart:
      max-file-size: 12MB
      max-request-size: 120MB
logging:
  level:
    org.hibernate.SQL: warn
storage:
  type: ${STORAGE_TYPE:local}
  local:
    dir: ${STORAGE_LOCAL_DIR:./uploads}
  oss:
    endpoint: ${OSS_ENDPOINT:https://oss-cn-beijing.aliyuncs.com}
    bucket: ${OSS_BUCKET:super-zzm}
    access-key-id: ${OSS_ACCESS_KEY_ID:CHANGE_ME}
    access-key-secret: ${OSS_ACCESS_KEY_SECRET:CHANGE_ME}
    base-url: ${OSS_BASE_URL:https://super-zzm.oss-cn-beijing.aliyuncs.com}
app:
  jwt:
    secret: ${JWT_SECRET:supermarket-local-dev-secret-change-me-32-bytes}
    expiration-seconds: ${JWT_EXPIRATION_SECONDS:86400}
    remember-expiration-seconds: ${JWT_REMEMBER_EXPIRATION_SECONDS:604800}
  order:
    pay-timeout-minutes: ${ORDER_PAY_TIMEOUT_MINUTES:15}
    close-check-interval-ms: ${ORDER_CLOSE_CHECK_INTERVAL_MS:60000}
  new-user-coupon-id: ${APP_NEW_USER_COUPON_ID:5}
  cache:
    enabled: ${APP_CACHE_ENABLED:false}
  idempotency:
    ttl-seconds: ${APP_IDEMPOTENCY_TTL_SECONDS:300}
YML

RUN mvn -q -B clean package -DskipTests

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
# 商品图 / 上传文件落在 ./uploads；云端请在 Railway backend 服务挂 Volume 到 /app/uploads 以持久化
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
