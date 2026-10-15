#!/usr/bin/env bash
# Phase 6 — Jenkins as code. JCasC + plugins.txt pinned. No UI clicks.
# Idempotent: safe to re-run. Existing build history is preserved (we don't wipe
# /var/lib/jenkins); JCasC reconciles system-wide config on every restart.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
[ -f "$REPO_ROOT/.env" ] && set -a && . "$REPO_ROOT/.env" && set +a

: "${ENV_NAME:?set in $REPO_ROOT/.env}"
: "${HOST_IP:?set in $REPO_ROOT/.env}"
: "${JENKINS_ADMIN_PASSWORD:?set in $REPO_ROOT/.env}"
: "${GIT_REPO_URL:?set in $REPO_ROOT/.env}"
: "${GIT_WEB_REPO_URL:?set in $REPO_ROOT/.env}"

JPM_VERSION="${JPM_VERSION:-2.13.2}"

log() { printf '\n=== %s ===\n' "$*"; }

# --- 1. jenkins-plugin-cli ---
if ! command -v jenkins-plugin-cli >/dev/null 2>&1; then
  log "install jenkins-plugin-cli (plugin-installation-manager-tool $JPM_VERSION)"
  tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
  curl -fsSL -o "$tmp/jpm.jar" \
    "https://github.com/jenkinsci/plugin-installation-manager-tool/releases/download/${JPM_VERSION}/jenkins-plugin-manager-${JPM_VERSION}.jar"
  sudo install -m 644 "$tmp/jpm.jar" /usr/local/lib/jenkins-plugin-manager.jar
  sudo tee /usr/local/bin/jenkins-plugin-cli >/dev/null <<'WRAPPER'
#!/bin/sh
exec java -jar /usr/local/lib/jenkins-plugin-manager.jar "$@"
WRAPPER
  sudo chmod +x /usr/local/bin/jenkins-plugin-cli
  trap - EXIT; rm -rf "$tmp"
fi
jenkins-plugin-cli --version

# --- 1b. credential files must exist BEFORE casc runs ---
# JCasC's ${readFile:...} / ${readFileBase64:...} expand to EMPTY for a missing
# file and log nothing. The credential is then created with an empty key and
# every SCM checkout dies with `error in libcrypto`, or kubectl falls back to
# localhost:8080. Fail here, where the cause is obvious.
log "check staged credential files"
missing=0
for f in /etc/jenkins/credentials/github_id_ed25519 /etc/jenkins/credentials/kubeconfig; do
  if sudo test -s "$f"; then
    echo "  ok      $f"
  else
    echo "  MISSING $f" >&2
    missing=1
  fi
done
if [ "$missing" -ne 0 ]; then
  cat >&2 <<'MSG'

Stage the missing file(s) first, then re-run:

  sudo install -d -m 750 -o root -g jenkins /etc/jenkins/credentials
  sudo install -m 640 -o root -g jenkins \
      ~/.ssh/fshrms_deploy /etc/jenkins/credentials/github_id_ed25519

See "Linking a host to GitHub" in server-setup/README.md for the deploy key,
and the jenkins-deployer service account for the kubeconfig.
MSG
  exit 1
fi

# --- 2. drop config files ---
log "stop jenkins so it picks up new plugins / casc cleanly"
sudo systemctl stop jenkins || true

log "drop casc.yaml + plugins.txt into /var/lib/jenkins"
sudo install -d -o jenkins -g jenkins -m 755 /var/lib/jenkins
sudo install -o jenkins -g jenkins -m 644 \
  "$REPO_ROOT/jenkins/casc.yaml"   /var/lib/jenkins/casc.yaml
sudo install -o jenkins -g jenkins -m 644 \
  "$REPO_ROOT/jenkins/plugins.txt" /var/lib/jenkins/plugins.txt

# --- 3. install plugins ---
log "install/update plugins"
sudo install -d -o jenkins -g jenkins /var/lib/jenkins/plugins
sudo -u jenkins jenkins-plugin-cli \
  --plugin-file /var/lib/jenkins/plugins.txt \
  --plugin-download-directory /var/lib/jenkins/plugins

# --- 4. systemd drop-in: env file for CASC_JENKINS_CONFIG + admin password ---
log "systemd drop-in + /etc/jenkins.env (root:600)"
sudo install -d -m 755 /etc/systemd/system/jenkins.service.d
sudo tee /etc/systemd/system/jenkins.service.d/override.conf >/dev/null <<'UNIT'
[Service]
EnvironmentFile=-/etc/jenkins.env
UNIT

# ENV_NAME and REGISTRY drive casc.yaml: which folder and per-service jobs get
# seeded (uat/<svc> vs prod/<svc>) and which registry pipelines push to. Each
# host builds and deploys only its own environment.
sudo tee /etc/jenkins.env >/dev/null <<EOF
CASC_JENKINS_CONFIG=/var/lib/jenkins/casc.yaml
JAVA_OPTS=-Djenkins.install.runSetupWizard=false
JENKINS_ADMIN_PASSWORD=${JENKINS_ADMIN_PASSWORD}
ENV_NAME=${ENV_NAME}
REGISTRY=${HOST_IP}:5001
HOST_IP=${HOST_IP}
GIT_REPO_URL=${GIT_REPO_URL}
GIT_WEB_REPO_URL=${GIT_WEB_REPO_URL}
EOF
sudo chmod 600 /etc/jenkins.env
sudo chown root:root /etc/jenkins.env
sudo systemctl daemon-reload

# --- 4b. jenkins ssh known_hosts ---
# Jenkins runs as its own user with its own $HOME and does NOT share
# ~/.ssh/known_hosts with the login user. Without this the first SCM clone
# hangs at host-key verification and the build never times out cleanly.
log "seed /var/lib/jenkins/.ssh/known_hosts"
sudo install -d -o jenkins -g jenkins -m 700 /var/lib/jenkins/.ssh
GIT_HOST=$(printf '%s' "$GIT_REPO_URL" | sed -E 's|^[a-z]+://||; s|^[^@]*@||; s|[:/].*$||')
for h in github.com "$GIT_HOST"; do
  if ! sudo -u jenkins ssh-keygen -F "$h" \
       -f /var/lib/jenkins/.ssh/known_hosts >/dev/null 2>&1; then
    ssh-keyscan -t rsa,ecdsa,ed25519 "$h" 2>/dev/null \
      | sudo -u jenkins tee -a /var/lib/jenkins/.ssh/known_hosts >/dev/null
    echo "  added host key: $h"
  else
    echo "  host key already present: $h"
  fi
done
sudo chown -R jenkins:jenkins /var/lib/jenkins/.ssh
sudo chmod 600 /var/lib/jenkins/.ssh/known_hosts

# A silently-empty known_hosts is the failure we are preventing: the clone
# would hang rather than error. Fail here instead — this script is idempotent.
if ! sudo test -s /var/lib/jenkins/.ssh/known_hosts; then
  echo "ERROR: known_hosts is empty — ssh-keyscan reached no host key." >&2
  echo "       Check outbound :22 to $GIT_HOST, then re-run this script." >&2
  exit 1
fi

# --- 4c. docker group ---
# The `docker build + push` stage shells out to the docker CLI as the jenkins
# user. Without membership in the `docker` group that's `permission denied ...
# unix:///var/run/docker.sock`. Group membership only takes on a fresh process,
# so this must land before the (re)start below.
log "ensure jenkins user is in the docker group"
if getent group docker >/dev/null 2>&1; then
  if id -nG jenkins | tr ' ' '\n' | grep -qx docker; then
    echo "  already a member"
  else
    sudo usermod -aG docker jenkins
    echo "  added jenkins to docker group"
  fi
else
  echo "WARNING: no 'docker' group on this host — is Docker installed?" >&2
fi

# --- 5. start ---
log "start jenkins"
sudo systemctl enable jenkins
sudo systemctl start jenkins

log "wait for /login to respond"
status="000"
for i in $(seq 1 60); do
  status=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/login || echo "000")
  case "$status" in 200|403) echo "Jenkins responding ($status)"; break ;; esac
  sleep 5
done
[ "$status" = "200" ] || [ "$status" = "403" ] || { echo "Jenkins not responding (last=$status)"; exit 1; }

curl -s -o /dev/null -w "Jenkins /login -> %{http_code}\n" http://localhost:8080/login
