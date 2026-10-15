# attendance-poller container. Runs as a long-lived loop that calls
# GET /devices/poller-targets every --interval seconds, pulls each
# active device over the ZK protocol, and forwards punches to
# attendance-service. The service owns the high-water-mark and the
# unmatched-card replay queue — this image holds no local state, so
# restarts are free.
#
# Build (the Dockerfile uses the poller dir as its build context):
#
#   cd services/attendance-poller
#   docker build -t attendance-poller:dev .
#
# Run:
#
#   docker run --rm --network host \
#     -e POLLER_BASE_URL=http://localhost:8083 \
#     -e POLLER_TOKEN="$JWT" \
#     attendance-poller:dev
#
# `--network host` is the simple option for an on-prem deploy: the
# container sits on the office LAN and can dial 10.10.107.x directly.

# ---------- builder ----------
FROM python:3.13-slim AS builder

ENV UV_LINK_MODE=copy \
    UV_COMPILE_BYTECODE=1 \
    UV_PROJECT_ENVIRONMENT=/app/.venv

RUN pip install --no-cache-dir uv==0.5.*

WORKDIR /build
COPY pyproject.toml uv.lock ./
COPY src ./src

# --frozen so the lockfile is authoritative; --no-dev drops ruff etc;
# --no-editable copies the project sources INTO the venv's site-packages
# so the runtime stage doesn't need /build/src to exist (the editable
# install's .pth file would otherwise point at a path that's only in
# the builder stage and ImportError at startup).
RUN uv sync --frozen --no-dev --no-editable

# ---------- runtime ----------
FROM python:3.13-slim

# Non-root with a fixed UID for k8s securityContext parity.
RUN groupadd -g 10001 poller \
 && useradd  -u 10001 -g 10001 -s /sbin/nologin -m -d /app poller

WORKDIR /app
COPY --from=builder --chown=10001:10001 /app/.venv /app/.venv

ENV PATH=/app/.venv/bin:$PATH \
    PYTHONUNBUFFERED=1 \
    TZ=Asia/Karachi

USER 10001:10001

# The CLI takes its config off the command line; run via:
#   attendance-poller-pull --base-url ... --token ... --interval 60
# An entrypoint script lets the compose / k8s manifest pass values via
# env without duplicating them on every command line.
COPY --chown=10001:10001 docker-entrypoint.sh /app/docker-entrypoint.sh

ENTRYPOINT ["/app/docker-entrypoint.sh"]
