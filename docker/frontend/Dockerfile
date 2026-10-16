# ========== 前端: Nginx + Vite 构建产物 ==========
FROM nginx:1.27-alpine AS runtime

LABEL maintainer="pcba-erp"
ENV TZ=Asia/Shanghai

# 替换默认 Nginx 配置 + 前端产物; compose 用 build.context=.. (pcba-erp 根)
COPY ./frontend/dist /usr/share/nginx/html/
COPY ./docker/nginx/nginx.conf /etc/nginx/templates/nginx.conf.template
ENV NGINX_ENVSUBST_OUTPUT_DIR=/etc/nginx NGINX_ENVSUBST_FILTER=^ERP_DOMAIN$ ERP_DOMAIN=erp.thinkfit.cn

# 日志与 tmp 目录权限 (alpine nginx uid=101)
RUN set -eux \
 && mkdir -p /var/log/nginx /var/cache/nginx /tmp \
 && chown -R nginx:nginx /var/log/nginx /var/cache/nginx /tmp /usr/share/nginx/html \
 && ln -sf /dev/stdout /var/log/nginx/access.log \
 && ln -sf /dev/stderr /var/log/nginx/error.log

EXPOSE 80
HEALTHCHECK --interval=20s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -Y off -qO- http://127.0.0.1:80/healthz >/dev/null 2>&1 || exit 1

STOPSIGNAL SIGQUIT
CMD ["nginx", "-g", "daemon off;"]
