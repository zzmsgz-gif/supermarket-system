#!/bin/sh
set -e
# 用环境变量 BACKEND_URL 渲染 nginx 配置模板；未设置时回退到本机 8080。
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
# 去掉结尾斜杠：proxy_pass 若以 "/" 结尾会进入路径替换模式，
# 把 /api 前缀剥掉（/api/x -> <backend>/x），导致 404。
BACKEND_URL="${BACKEND_URL%/}"
export BACKEND_URL

envsubst '${BACKEND_URL}' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf
