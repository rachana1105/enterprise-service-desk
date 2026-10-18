# React/Vite frontend served by Nginx, which also proxies /api to the backend.
ARG NODE_IMAGE=node:22-alpine
ARG NGINX_IMAGE=nginx:alpine

FROM ${NODE_IMAGE} AS frontend
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-fund --no-audit
COPY frontend/ ./
RUN npm run build

FROM ${NGINX_IMAGE}
# Apply Alpine security updates published after the base image was built
# (e.g. libexpat 2.9.0-r0 for CVE-2026-77214 reported by Trivy).
RUN apk upgrade --no-cache
COPY --from=frontend /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
