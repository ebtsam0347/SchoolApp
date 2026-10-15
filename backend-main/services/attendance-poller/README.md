# attendance-poller

Python sidecar that pulls punches from ZKTeco-style biometric devices over the
ZK protocol (TCP 4370) and forwards them to attendance-service. Lives
alongside the Java services in `services/` because it's part of the HRMS
runtime — it just isn't on the JVM.

The poller is **stateless and table-driven**. attendance-service owns the
device catalog (active flag, comm password, IP), the high-water-mark, and
the unmatched-card replay queue. The poller asks the service "what should I
poll right now?" and does it.

Three CLIs:

- `attendance-poller-pull` — long-lived loop. Each tick:
  `GET /attendance/devices/poller-targets` → for each active device, open ZK
  socket, pull all records past the per-device high-water-mark, batch-POST
  to `/attendance/punches/ingest`, heartbeat. Use `--once` for cron / k8s
  CronJob mode if you don't want a long-running process.
- `attendance-poller-sync` — single sync-drain cycle: claim a batch of
  device-side tasks (enroll user / delete user / push fingerprint
  template), execute via pyzk, report back. Still uses the per-device
  `--device-id/--host` flags — table-driven mode for sync is a follow-up.
- `attendance-poller-dump` — one-shot snapshot of a device for offline
  analysis.

## Setup

Uses `uv` for env + deps. From this directory:

```bash
uv sync
```

That creates `.venv/` and installs `pyzk` + `httpx`.

## Pulling punches into the attendance service

```bash
uv run attendance-poller-pull \
  --base-url http://localhost:8083 \
  --token "$POLLER_JWT" \
  --interval 60
```

The poller calls `GET /attendance/devices/poller-targets` each tick. Active
devices with a non-null `device_ip` get pulled; inactive ones are skipped.
HR flips a device's `active` flag to stop polling — no poller restart
required.

What happens each ingest call:

1. Server inserts known-card punches as `BIOMETRIC` rows, advances the
   per-device `last_punched_at` to `max(punchedAt)` of the batch.
2. Unknown-card punches go into `attendance_punch_unmatched`. A
   scheduled job in attendance-service drains the queue whenever HR adds
   the missing bridge row (every 5 min by default).
3. Server returns `{ingested, skipped}` for telemetry — the poller logs
   it but does not retry locally; the server owns the retry.

The `--token` is a bearer JWT for a service account with the
`attendance.poller` permission. Long-lived and configured per env;
automatic JWT login + refresh against auth-service is a follow-up.

## Network

The on-prem device sits on the office LAN (10.10.107.0/24). It's not
reachable from a laptop on a different VLAN — run the dump or live pull
from a host that can hit `10.10.107.21` directly. UAT/Prod runs as a k8s
deployment on the single-VM cluster, which is on the device's subnet (or
routed there).

## Running in Docker

There's a multistage `Dockerfile` here. Build:

```bash
cd services/attendance-poller
docker build -t fshrms/attendance-poller:dev .
```

Run (host network so the container can dial the office LAN):

```bash
docker run --rm --network host \
  -e POLLER_BASE_URL=http://localhost:8083 \
  -e POLLER_TOKEN="$POLLER_JWT" \
  -e POLLER_INTERVAL=60 \
  fshrms/attendance-poller:dev
```

Or via compose — the `attendance-poller` service in repo-root
`docker-compose.yml` is wired to bring up the poller alongside the rest
of the stack:

```bash
docker compose --profile app up -d --build
```

The compose poller needs IP-level reachability to the readers. On Linux
add `network_mode: host`; on Mac/Windows attach the office LAN subnet to
the docker network.

### Env vars consumed by the entrypoint

| Var | Purpose |
| --- | --- |
| `POLLER_BASE_URL` | attendance-service base URL (required). |
| `POLLER_TOKEN` | Bearer JWT with `attendance.poller`. |
| `POLLER_INTERVAL` | Sweep interval seconds (default 60). |
| `POLLER_BATCH_SIZE` | Punches per ingest call (default 200). |
| `POLLER_ONCE` | `true` → single sweep, exit. |
| `POLLER_VERBOSE` | `true` for DEBUG-level logging. |
| `POLLER_IDENTITY_USER_ID` / `POLLER_IDENTITY_USERNAME` / `POLLER_IDENTITY_PERMISSIONS` / `POLLER_IDENTITY_ROLES` | Service-account identity injected as `X-User-*` headers when calling attendance-service directly (bypassing the gateway). |

## ZK status / punch encoding

Values come straight from the device:

- `status`: `0` check-in, `1` check-out, `2` break-out, `3` break-in,
  `4` OT-in, `5` OT-out.
- `punch`: `0` fingerprint, `1` password, `2` card, `4` face, `15` face
  (newer firmware).

The poller doesn't act on these — `attendance-service` derives in/out
from first/last `punched_at` per work date — but the dump preserves
them for forensics.

## Pushing user / template changes to a device

`attendance-poller-sync` still uses the older single-device shape:

```bash
uv run attendance-poller-sync \
  --device-id 7 \
  --host 10.10.107.21 \
  --base-url http://localhost:8083 \
  --token "$POLLER_JWT"
```

Each invocation:

1. Claims up to `--batch-size` (default 10) tasks via
   `GET /devices/{id}/sync-tasks/next`.
2. For each task:
   - `UPSERT_USER` → `conn.set_user(user_id=employeeCode, name=…, privilege=…)`,
     plus `delete_user(user_id=oldEmployeeCode)` first if the payload carries
     an employee_code change.
   - `DELETE_USER` → `conn.delete_user(user_id=employeeCode)`.
   - `PUSH_TEMPLATE` (FINGERPRINT) → `GET /templates/{id}` to fetch bytes,
     locate the user on the device, then
     `conn.save_user_template(user, [Finger])`.
   - `PUSH_TEMPLATE` (FACE) → fails non-retryably (pyzk 0.9 has no
     face-write API; firmware-specific call needed before re-enabling).
3. Reports each outcome via `…/complete` (with a one-line `detail` string)
   or `…/fail` (`retryable=true` for transient ZK errors,
   `retryable=false` for shape / capability problems).

## Dumping a device

```bash
uv run attendance-poller-dump \
  --host 10.10.107.21 \
  --port 4370 \
  --password 0
```

Output goes to `migration/attendance_device/<ip>_<utc-stamp>/` with three
files:

- `device_info.json` — firmware, serial, platform, network params,
  device clock.
- `users.json` — `{ count, users: [{ uid, user_id, name, privilege,
  group_id, card }] }`. Passwords are intentionally not dumped.
- `attendance.json` — `{ count, records: [{ user_id, timestamp, status,
  punch, uid }] }`.

The device is `disable_device()`'d for the duration of the read and
re-enabled on exit (including on error), so users can't punch into a
half-read state.

## What's next

- Service-account JWT login + refresh against auth-service (replaces the
  static `--token` flag).
- Same table-driven shape for `attendance-poller-sync` (single binary,
  one flag set, server-driven device list).
- FACE template push: identify the deployed firmware and wire the right
  pyzk extension / raw command.
- k8s manifest under `services/attendance-poller/k8s/` (image already
  builds; manifest still pending).
