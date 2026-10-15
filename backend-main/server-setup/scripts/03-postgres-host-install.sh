#!/usr/bin/env bash
# Phase 2 — Postgres 17 on the host (PGDG repo, tuning, pg_hba, role, DB).
# One environment per host: the role and database are both named $ENV_NAME.
# Idempotent: safe to re-run.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
[ -f "$REPO_ROOT/.env" ] && set -a && . "$REPO_ROOT/.env" && set +a

: "${ENV_NAME:?set in $REPO_ROOT/.env (see .env.example)}"
: "${PG_PASSWORD:?set in $REPO_ROOT/.env (see .env.example)}"

log() { printf '\n=== %s ===\n' "$*"; }

log "1. PGDG apt repo"
sudo install -d /usr/share/postgresql-common/pgdg
sudo curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc \
  -o /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc
echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] https://apt.postgresql.org/pub/repos/apt noble-pgdg main" \
  | sudo tee /etc/apt/sources.list.d/pgdg.list >/dev/null

log "2. install postgresql-17"
sudo apt-get update -y
sudo apt-get install -y postgresql-17

log "3. tuning drop-in (/etc/postgresql/17/main/conf.d/99-fshrms.conf)"
sudo install -d -m 755 /etc/postgresql/17/main/conf.d
sudo tee /etc/postgresql/17/main/conf.d/99-fshrms.conf >/dev/null <<'CONF'
# Managed by server-setup/scripts/03-postgres-host-install.sh
listen_addresses = '*'
shared_buffers = 2GB
max_connections = 200
work_mem = 16MB
effective_cache_size = 16GB
wal_compression = on
log_min_duration_statement = 500ms
log_line_prefix = '%m [%p] %q%u@%d '
CONF

# Ubuntu PGDG postgresql.conf already has `include_dir = 'conf.d'`. If a future
# Postgres release ever ships without it, append once.
if ! grep -qE "^[[:space:]]*include_dir[[:space:]]*=[[:space:]]*'conf\.d'" \
       /etc/postgresql/17/main/postgresql.conf; then
  echo "include_dir = 'conf.d'" \
    | sudo tee -a /etc/postgresql/17/main/postgresql.conf >/dev/null
fi

log "4. pg_hba.conf — localhost + docker bridges + k3s pod CIDR"
sudo tee /etc/postgresql/17/main/pg_hba.conf >/dev/null <<'HBA'
# Managed by server-setup/scripts/03-postgres-host-install.sh
# TYPE  DATABASE        USER            ADDRESS                 METHOD
local   all             postgres                                peer
local   all             all                                     peer
host    all             all             127.0.0.1/32            scram-sha-256
host    all             all             ::1/128                 scram-sha-256
# Docker bridge networks — covers the default bridge (172.17/16) and any
# compose-created bridges (172.18+/16). Lets pgadmin and any host-compose
# container reach Postgres via host.docker.internal.
host    all             all             172.16.0.0/12           scram-sha-256
# k3s pod CIDR.
host    all             all             10.42.0.0/16            scram-sha-256
HBA

log "5. enable + restart"
sudo systemctl enable postgresql@17-main
sudo systemctl restart postgresql@17-main

log "6. role + database ($ENV_NAME)"
# psql :'var' substitution doesn't reach inside $$..$$ blocks, so we do
# CREATE-if-missing via \gexec (no password) then always ALTER … PASSWORD.
# :'env' is a string literal, :"env" a quoted identifier.
sudo -u postgres psql -v ON_ERROR_STOP=1 \
  -v env="$ENV_NAME" \
  -v pw="$PG_PASSWORD" <<'SQL'
SELECT 'CREATE ROLE ' || quote_ident(:'env') || ' LOGIN'
 WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'env')\gexec

ALTER ROLE :"env" WITH LOGIN PASSWORD :'pw';

SELECT 'CREATE DATABASE ' || quote_ident(:'env') || ' OWNER ' || quote_ident(:'env')
 WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'env')\gexec

-- Only the owner may CONNECT. Owner privileges are implicit, so the GRANT is
-- redundant for the role itself; the REVOKE blocks every other role.
REVOKE CONNECT ON DATABASE :"env" FROM PUBLIC;
GRANT  CONNECT ON DATABASE :"env" TO :"env";
SQL

log "done — server status"
sudo -u postgres psql -At -c "SELECT version();"
# psql does NOT interpolate :'var' in -c strings — only in stdin/file input.
# Same reason the block above is a heredoc.
sudo -u postgres psql -At -v env="$ENV_NAME" <<'SQL'
SELECT 'role: ' || rolname FROM pg_roles    WHERE rolname = :'env';
SELECT 'db:   ' || datname FROM pg_database WHERE datname = :'env';
SQL
