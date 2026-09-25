# syntax=docker/dockerfile:1
# ============================================================
# 超市系统前端镜像（多阶段：node 构建 → nginx 托管）
#   - 构建期：npm install + vite build 产出 dist/
#   - 运行期：nginx 托管静态资源，并把 /api 反代到 BACKEND_URL
#   - 前端用相对路径 /api 调接口，浏览器视角同源，无需 CORS
# ============================================================
FROM node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf.template /etc/nginx/conf.d/default.conf.template
# Railway frontend 变量 BACKEND_URL 会覆盖此默认值（填 backend 服务的 *.up.railway.app 域名）
ENV BACKEND_URL=http://localhost:8080
EXPOSE 80
# 仅渲染模板里的 ${BACKEND_URL}；$uri / $host 等 nginx 内置变量不受影响
CMD ["/bin/sh", "-c", "envsubst '$$BACKEND_URL' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf && exec nginx -g 'daemon off;'"]
