#!/usr/bin/env bash
# Phase 0 — OS hygiene. Snapshot the VM in VMware before running.
# Reboots if the kernel/initrd was upgraded.

set -euo pipefail

log() { printf '\n=== %s ===\n' "$*"; }

log "ssh service must stay enabled"
systemctl is-enabled ssh

log "apt update + full-upgrade + autoremove"
sudo apt-get update -y
sudo DEBIAN_FRONTEND=noninteractive apt-get -y \
  -o Dpkg::Options::="--force-confdef" \
  -o Dpkg::Options::="--force-confold" \
  full-upgrade
sudo apt-get autoremove -y --purge

if [ -f /var/run/reboot-required ]; then
  log "reboot-required flag set — rebooting in 10s (Ctrl-C to abort)"
  sleep 10
  sudo reboot
else
  log "no reboot required"
fi
