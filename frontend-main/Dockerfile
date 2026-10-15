# Multi-stage build for the FSHRMS web frontend.
#
# Build stage: Node 22 alpine compiles a static Vite bundle.
# Runtime stage: nginxinc/nginx-unprivileged (runs as UID 101, listens on
# 8080 — no root, no setcap dance). The image's stock entrypoint already
# runs envsubst on /etc/nginx/templates/*.template, so we drop the
# template in there and let it render /etc/nginx/conf.d/default.conf at
# container start. NGINX_ENVSUBST_FILTER pins which env vars get
# substituted so nginx's own $host / $remote_addr / etc. survive.
#
# UPSTREAM_GATEWAY is supplied per-environment by the k8s ConfigMap
# (e.g. http://gateway.uat.svc.cluster.local:8080). One image, two envs —
# matches the "promote by tag, never rebuild" rule from CLAUDE.md.

# --- build stage ---
FROM node:22-alpine AS build
WORKDIR /app

# Install deps with a clean lockfile-respecting install.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# Copy the rest and build. .env.production is picked up automatically by
# Vite for `vite build`, baking VITE_API_BASE=/api into the bundle.
COPY . .
RUN npm run build

# --- runtime stage ---
FROM nginxinc/nginx-unprivileged:1.27-alpine

# Substitute only this specific var — leaves nginx's own $host /
# $remote_addr / $request_uri / etc. untouched.
ENV NGINX_ENVSUBST_FILTER=^UPSTREAM_GATEWAY$
ENV UPSTREAM_GATEWAY=http://localhost:8080

COPY --chown=101:101 nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build --chown=101:101 /app/dist /usr/share/nginx/html

EXPOSE 8080
