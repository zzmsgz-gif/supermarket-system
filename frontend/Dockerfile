# 多阶段构建：node 构建 Vue，nginx 托管静态资源并反代 /api 到后端
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine
RUN apk add --no-cache gettext
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf.template /etc/nginx/conf.d/default.conf.template
COPY docker-entrypoint.sh /docker-entrypoint.d/40-backend-url.sh
RUN chmod +x /docker-entrypoint.d/40-backend-url.sh
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
