#!/usr/bin/env bash
# Phase 4 — k3s platform layer:
#   namespaces ($ENV_NAME, observability, platform, ingress)
#   cert-manager + internal-CA ClusterIssuer
#   sealed-secrets (controller + public cert dump)
#   kube-prometheus-stack (Prom + Grafana + Alertmanager)
#   loki-stack (Loki + Promtail)
#   zipkin
#
# Idempotent: helm upgrade --install, kubectl apply.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
[ -f "$REPO_ROOT/.env" ] && set -a && . "$REPO_ROOT/.env" && set +a

: "${ENV_NAME:?set in $REPO_ROOT/.env}"
: "${HOST_IP:?set in $REPO_ROOT/.env}"
: "${GRAFANA_ADMIN_PASSWORD:?set in $REPO_ROOT/.env}"

# Prod serves on the real domain; every other environment stays on the .local
# LAN convention, subdomained under its own name so a laptop's /etc/hosts can
# point at both VMs at once. Override per host with HOST_SUFFIX in .env.
if [ -z "${HOST_SUFFIX:-}" ]; then
  if [ "$ENV_NAME" = "prod" ]; then
    HOST_SUFFIX="hrms.forward.pk"
  else
    HOST_SUFFIX="${ENV_NAME}.hrms.local"
  fi
fi

# The observability UIs never leave the LAN (Prometheus, Alertmanager and
# Zipkin have no auth), so they keep a .local name regardless of what the
# public suffix is. `.local` also carries no HSTS policy, which is what lets
# them stay on plain HTTP with no certificate to manage.
INTERNAL_SUFFIX="${INTERNAL_SUFFIX:-${ENV_NAME}.hrms.local}"

# Prod serves the frontend at the bare suffix (hrms.forward.pk) because that is
# what a user types; every other env keeps the app. prefix so both VMs can sit
# in one /etc/hosts without colliding. The gateway is always api.<suffix>.
if [ "$ENV_NAME" = "prod" ]; then
  APP_HOST="${APP_HOST:-${HOST_SUFFIX}}"
else
  APP_HOST="${APP_HOST:-app.${HOST_SUFFIX}}"
fi
# The API hostname is LAN-only in every environment — the frontend proxies
# /api/ to the gateway in-cluster, so nothing publishes it. It therefore takes
# the INTERNAL suffix: a `*.forward.pk` name would inherit the parent domain's
# HSTS policy and a browser would refuse to load it over plain HTTP.
API_HOST="${API_HOST:-api.${INTERNAL_SUFFIX}}"

# Staging by default: Let's Encrypt production rate-limits failed validations
# hard, and a wrong NAT can burn the weekly allowance in minutes. Flip to
# letsencrypt-prod in .env once a staging cert reaches Ready.
# Public high port the office router forwards to this node. Only prod has one;
# a LoadBalancer Service holds the host port open (see web-lan-service.yaml).
LAN_PORT="${LAN_PORT:-5000}"

# Prod sits behind the cPanel [P] reverse proxy, which cannot set
# ProxyPreserveHost, so the Host header arriving at Traefik may read
# `<public-ip>:<port>` rather than the public name — a Host() rule would 404
# every proxied request. Other envs are reached directly by hostname and keep
# a Host() rule, so this service does not become the node's default backend.
if [ "$ENV_NAME" = "prod" ]; then
  WEB_MATCH='PathPrefix(`/`)'
else
  WEB_MATCH="Host(\`${APP_HOST}\`)"
fi

ACME_ISSUER="${ACME_ISSUER:-letsencrypt-staging}"
ACME_EMAIL="${ACME_EMAIL:-admin@forward.pk}"

KUBESEAL_VERSION="${KUBESEAL_VERSION:-0.27.1}"

log() { printf '\n=== %s ===\n' "$*"; }

# --- 0. kubeconfig for the invoking user ---
# Two separate problems here:
#   1. k3s writes /etc/rancher/k3s/k3s.yaml as root:root 0600, so a normal user
#      cannot read it.
#   2. k3s installs kubectl as a symlink to the k3s binary, and that wrapper
#      DEFAULTS to /etc/rancher/k3s/k3s.yaml when KUBECONFIG is unset — it does
#      not fall back to ~/.kube/config the way upstream kubectl does.
# So seeding the user copy is not enough; KUBECONFIG has to point at it.
# Deliberately not using k3s --write-kubeconfig-mode 644: that makes
# cluster-admin credentials readable by every account, and this host runs
# Jenkins.
export KUBECONFIG="${KUBECONFIG:-$HOME/.kube/config}"
if ! kubectl cluster-info >/dev/null 2>&1; then
  if [ -f /etc/rancher/k3s/k3s.yaml ]; then
    log "seed $KUBECONFIG from k3s"
    install -d -m 700 "$(dirname "$KUBECONFIG")"
    # Redirect from `sudo cat` rather than `sudo cp` so the file ends up owned
    # by this user, not root.
    sudo cat /etc/rancher/k3s/k3s.yaml > "$KUBECONFIG"
    chmod 600 "$KUBECONFIG"
  else
    echo "ERROR: kubectl cannot reach a cluster and /etc/rancher/k3s/k3s.yaml" >&2
    echo "       is missing. Is k3s installed and running on this host?" >&2
    exit 1
  fi
fi
# Persist for interactive shells, or every new login hits the same wall.
if ! grep -qs 'KUBECONFIG=$HOME/.kube/config' "$HOME/.bashrc"; then
  printf '\n# added by server-setup/scripts/06-platform-up.sh\nexport KUBECONFIG=$HOME/.kube/config\n' >> "$HOME/.bashrc"
  echo "  added KUBECONFIG export to ~/.bashrc"
fi
kubectl cluster-info 2>/dev/null | head -1

# --- 1. kubeseal CLI (one-time install) ---
# Run sudo-cache-priming once in the calling shell before invoking this script
# (e.g. `sudo -v && ./scripts/06-platform-up.sh`).
if ! command -v kubeseal >/dev/null 2>&1; then
  log "install kubeseal v$KUBESEAL_VERSION"
  tmp=$(mktemp -d)
  trap 'rm -rf "$tmp"' EXIT
  curl -fsSL "https://github.com/bitnami/sealed-secrets/releases/download/v${KUBESEAL_VERSION}/kubeseal-${KUBESEAL_VERSION}-linux-amd64.tar.gz" \
    -o "$tmp/kubeseal.tar.gz"
  tar -xzf "$tmp/kubeseal.tar.gz" -C "$tmp" kubeseal
  sudo install -m 755 "$tmp/kubeseal" /usr/local/bin/kubeseal
  trap - EXIT
  rm -rf "$tmp"
fi
kubeseal --version

# --- 2. helm repos ---
log "helm repos"
helm repo add jetstack https://charts.jetstack.io --force-update
# Chart repo moved with the org rename (bitnami-labs -> bitnami); the old
# bitnami-labs.github.io gh-pages URL now 404s.
helm repo add sealed-secrets https://bitnami.github.io/sealed-secrets --force-update
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts --force-update
helm repo add grafana https://grafana.github.io/helm-charts --force-update
helm repo update

# --- 3. namespaces + quotas + limit ranges ---
log "namespaces ($ENV_NAME + observability, platform, ingress)"
sed "s|ENV_NAME_PLACEHOLDER|${ENV_NAME}|g" "$REPO_ROOT/k8s/namespaces.yaml" \
  | kubectl apply -f -

# --- 4. cert-manager (in `ingress` ns, alongside future cert-related infra) ---
log "cert-manager"
helm upgrade --install cert-manager jetstack/cert-manager \
  --namespace ingress \
  --set crds.enabled=true \
  --wait --timeout 5m

log "wait for cert-manager webhook to be ready before applying ClusterIssuer"
kubectl -n ingress wait --for=condition=Available deploy/cert-manager-webhook --timeout=120s

log "internal-ca ClusterIssuer + bootstrap"
kubectl apply -f "$REPO_ROOT/k8s/platform/cluster-issuer-internal.yaml"
kubectl -n ingress wait --for=condition=Ready certificate/internal-ca --timeout=120s

# ACME issuers, for environments serving on a real public domain. Applying them
# costs nothing on a host that never requests an ACME cert — no Certificate
# references the issuer unless ACME_ISSUER points at it.
log "ACME cluster issuers (account email: ${ACME_EMAIL})"
sed "s|ACME_EMAIL_PLACEHOLDER|${ACME_EMAIL}|g" \
  "$REPO_ROOT/k8s/platform/cluster-issuer-acme.yaml" | kubectl apply -f -

# --- 5. sealed-secrets ---
log "sealed-secrets"
helm upgrade --install sealed-secrets sealed-secrets/sealed-secrets \
  --namespace platform \
  --wait --timeout 5m

# Each cluster generates its own keypair, so the cert filename carries the env.
# A sealed secret encrypted on one VM cannot be decrypted on the other.
CERT="$REPO_ROOT/k8s/platform/sealed-secrets-${ENV_NAME}.pub.pem"
log "fetch sealed-secrets public cert -> $(basename "$CERT")"
kubeseal --controller-name sealed-secrets --controller-namespace platform \
  --fetch-cert > "$CERT"
ls -la "$CERT"

# --- 6. kube-prometheus-stack ---
log "kube-prometheus-stack (Prom + Grafana + Alertmanager)"
helm upgrade --install kps prometheus-community/kube-prometheus-stack \
  --namespace observability \
  --values "$REPO_ROOT/k8s/platform/observability/kps-values.yaml" \
  --set grafana.adminPassword="$GRAFANA_ADMIN_PASSWORD" \
  --wait --timeout 10m

# --- 7. loki + promtail (active charts; loki-stack is deprecated) ---
# If a previous install used the deprecated grafana/loki-stack chart under the
# release name "loki", uninstall it first — its resources collide with the new chart.
if helm -n observability list -o json 2>/dev/null \
    | grep -q '"name":"loki"[^}]*"chart":"loki-stack-'; then
  log "migrate: uninstall deprecated loki-stack release"
  helm -n observability uninstall loki --wait
  kubectl -n observability delete pvc storage-loki-0 --ignore-not-found
fi

log "loki (single-binary)"
helm upgrade --install loki grafana/loki \
  --namespace observability \
  --values "$REPO_ROOT/k8s/platform/observability/loki-values.yaml" \
  --wait --timeout 10m

# Promtail chart is deprecated (Grafana moved to Alloy). Uninstall if present.
if helm -n observability status promtail >/dev/null 2>&1; then
  log "migrate: uninstall deprecated promtail release"
  helm -n observability uninstall promtail --wait
fi

log "alloy (daemonset, log scraper)"
helm upgrade --install alloy grafana/alloy \
  --namespace observability \
  --values "$REPO_ROOT/k8s/platform/observability/alloy-values.yaml" \
  --wait --timeout 5m

# --- 8. zipkin ---
log "zipkin"
kubectl apply -f "$REPO_ROOT/k8s/platform/observability/zipkin.yaml"
kubectl -n observability rollout status deploy/zipkin --timeout=120s

# --- 9. fshrms grafana dashboards (overview / service detail / logs) ---
log "fshrms dashboards"
kubectl apply -k "$REPO_ROOT/k8s/platform/observability/dashboards"

# --- 10. ingress routes for observability UIs ---
log "observability ingress (*.${INTERNAL_SUFFIX}, LAN only, plain HTTP)"
sed "s|INTERNAL_SUFFIX_PLACEHOLDER|${INTERNAL_SUFFIX}|g" \
  "$REPO_ROOT/k8s/ingress/observability-ingress.yaml" | kubectl apply -f -

# --- 11. ingress route for the frontend (`web` lands via Jenkins later) ---
log "web ingress (${APP_HOST} + ${API_HOST} -> ns ${ENV_NAME})"
sed -e "s|ENV_NAME_PLACEHOLDER|${ENV_NAME}|g" \
    -e "s|APP_HOST_PLACEHOLDER|${APP_HOST}|g" \
    -e "s|API_HOST_PLACEHOLDER|${API_HOST}|g" \
    -e "s|WEB_MATCH_PLACEHOLDER|${WEB_MATCH}|g" \
  "$REPO_ROOT/k8s/ingress/web-ingress.yaml" | kubectl apply -f -

# Prod only: hold host port $LAN_PORT open for the office router's forward.
# UAT is reached over the LAN by hostname and needs no such port.
if [ "$ENV_NAME" = "prod" ]; then
  log "web LAN LoadBalancer (host port ${LAN_PORT} -> web pods)"
  sed -e "s|ENV_NAME_PLACEHOLDER|${ENV_NAME}|g" \
      -e "s|LAN_PORT_PLACEHOLDER|${LAN_PORT}|g" \
    "$REPO_ROOT/k8s/ingress/web-lan-service.yaml" | kubectl apply -f -
fi

log "platform pods"
kubectl get pods -n ingress
kubectl get pods -n platform
kubectl get pods -n observability

log "done"
cat <<MSG

Observability is up. The UIs are LAN-only — add to your laptop's /etc/hosts:
  ${HOST_IP} grafana.${INTERNAL_SUFFIX} prometheus.${INTERNAL_SUFFIX} alertmanager.${INTERNAL_SUFFIX} zipkin.${INTERNAL_SUFFIX}

  Grafana       http://grafana.${INTERNAL_SUFFIX}       (admin / \$GRAFANA_ADMIN_PASSWORD)
  Prometheus    http://prometheus.${INTERNAL_SUFFIX}
  Alertmanager  http://alertmanager.${INTERNAL_SUFFIX}
  Zipkin        http://zipkin.${INTERNAL_SUFFIX}

The cluster serves the app over PLAIN HTTP on port 80. TLS terminates
upstream, at the cPanel host that owns the public A record and AutoSSL
certificate and reverse-proxies inward over a forwarded high port:

  Frontend      http://${APP_HOST}             (LAN; after the Jenkins web build)
  API           http://${API_HOST}             (LAN; /etc/hosts)

Public entry is whatever cPanel publishes, e.g. https://hrms.forward.pk.
The frontend proxies /api/ to the gateway in-cluster, so only the app
hostname needs to be public.

  Route status:  kubectl -n ${ENV_NAME} get ingressroute

In Grafana → Dashboards → FSHRMS folder you'll find:
  - FSHRMS — Overview         all 9 services at a glance
  - FSHRMS — Service detail   drill-in (HTTP / JVM / DB / Kafka / logs)
  - FSHRMS — Logs             Loki explorer with traceId → Zipkin
MSG
