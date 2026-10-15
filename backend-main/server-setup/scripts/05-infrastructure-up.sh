#!/usr/bin/env bash
# Phase 3 — Host docker compose: redis, kafka KRaft, minio, registry,
# kafka-ui, redisinsight, pgadmin. One environment per host.
# Idempotent: safe to re-run.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
[ -f "$REPO_ROOT/.env" ] && set -a && . "$REPO_ROOT/.env" && set +a

: "${ENV_NAME:?set in $REPO_ROOT/.env}"
: "${REDIS_PASSWORD:?set in $REPO_ROOT/.env}"
: "${MINIO_ROOT_USER:?set in $REPO_ROOT/.env}"
: "${MINIO_ROOT_PASSWORD:?set in $REPO_ROOT/.env}"
: "${PGADMIN_EMAIL:?set in $REPO_ROOT/.env}"
: "${PGADMIN_PASSWORD:?set in $REPO_ROOT/.env}"
: "${HOST_IP:?set in $REPO_ROOT/.env — this host LAN-reachable IP}"

log() { printf '\n=== %s ===\n' "$*"; }

# Generate KAFKA_CLUSTER_ID once and persist. Apache Kafka KRaft expects a
# 22-char base64 UUID; anything else makes the broker crash-loop.
if [ -z "${KAFKA_CLUSTER_ID:-}" ]; then
  KAFKA_CLUSTER_ID=$(openssl rand -base64 16 | tr '+/' '-_' | tr -d '=' | head -c 22)
  if grep -q '^KAFKA_CLUSTER_ID=' "$REPO_ROOT/.env"; then
    sed -i.bak "s|^KAFKA_CLUSTER_ID=.*|KAFKA_CLUSTER_ID=$KAFKA_CLUSTER_ID|" "$REPO_ROOT/.env"
    rm -f "$REPO_ROOT/.env.bak"
  else
    printf '\nKAFKA_CLUSTER_ID=%s\n' "$KAFKA_CLUSTER_ID" >> "$REPO_ROOT/.env"
  fi
  export KAFKA_CLUSTER_ID
  log "Generated KAFKA_CLUSTER_ID=$KAFKA_CLUSTER_ID (saved to .env)"
fi

log "compose up -d"
cd "$REPO_ROOT/infrastructure"
docker compose --env-file "$REPO_ROOT/.env" up -d --remove-orphans

log "wait for healthchecks"
for svc in redis kafka minio registry; do
  printf '  %-12s ' "$svc"
  status="starting"
  for i in $(seq 1 60); do
    status=$(docker inspect -f '{{.State.Health.Status}}' "$svc" 2>/dev/null || echo "missing")
    case "$status" in
      healthy)  echo "healthy"; break ;;
      missing)  echo "(not running)"; break ;;
    esac
    sleep 2
  done
  if [ "$status" != "healthy" ] && [ "$status" != "missing" ]; then
    echo "still $status after 120s"
  fi
done

log "service status"
docker compose ps

# --- Kafka topic bootstrap ---
# auto.create.topics.enable is off in apache/kafka by default; without
# pre-creating topics every service's first publish hits
# UnknownTopicOrPartition and Spring's outbox poller spins. Per-svc audit
# + events topics are stable and small — bootstrap them once.
log "kafka topic bootstrap (idempotent)"
TOPICS=(
  attendance.audit attendance.events
  auth.audit auth.events
  chat.audit chat.events
  employee.audit employee.events
  hrms.audit
  notification.audit
  payroll.audit payroll.events
  # Off-convention name (not <svc>.events) — payroll's AnnualLeaveEvents.TOPIC
  # publishes here via the outbox. Kept as-is to match the code; renaming it is
  # a code change, not a broker one.
  annual-leave
)
for t in "${TOPICS[@]}"; do
  docker exec kafka /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server localhost:9092 \
    --create --if-not-exists \
    --topic "$t" --partitions 3 --replication-factor 1 \
    2>&1 | grep -vE "^WARNING.*period|^$" || true
done
docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list | sort | sed 's/^/  /'
