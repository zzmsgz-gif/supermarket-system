#!/bin/sh
set -e
# 用环境变量 BACKEND_URL 渲染 nginx 配置模板；未设置时回退到本机 8080。
export BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
envsubst '${BACKEND_URL}' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf
