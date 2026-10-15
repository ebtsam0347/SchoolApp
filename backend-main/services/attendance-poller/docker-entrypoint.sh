#!/bin/sh
# Translate env vars to flags for attendance-poller-pull. Keeping the
# CLI as the only config surface (no settings file, no env-binding in
# Python) means the container, k8s manifest, and `uv run` invocations
# all look the same on the host.
set -eu

: "${POLLER_BASE_URL:?POLLER_BASE_URL is required}"

cmd="attendance-poller-pull --base-url $POLLER_BASE_URL --interval ${POLLER_INTERVAL:-60} --batch-size ${POLLER_BATCH_SIZE:-200} --sync-batch-size ${POLLER_SYNC_BATCH_SIZE:-50}"

if [ -n "${POLLER_TOKEN:-}" ]; then
    cmd="$cmd --token $POLLER_TOKEN"
fi

if [ "${POLLER_VERBOSE:-}" = "true" ]; then
    cmd="$cmd -v"
fi

if [ "${POLLER_ONCE:-}" = "true" ]; then
    cmd="$cmd --once"
fi

# Pass through any extra args from `command:` overrides untouched.
exec $cmd "$@"
